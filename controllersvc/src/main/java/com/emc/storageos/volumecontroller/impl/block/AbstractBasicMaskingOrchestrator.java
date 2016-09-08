/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.util.StringUtils;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportRemoveVolumesOnAdoptedMaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.ExportPathUpdater;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

/**
 * This class will have common code used by the MaskingOrchestrator implementations. It
 * also provides a simple, default implementation of the export operations,
 * which assumes that the ExportMasks on the array will only be created by the system.
 * Any existing exports maybe clobber or the operation may fail in such scenarios.
 *
 * Please note this class implements the zoning workflow first and then masking workflow.
 */
abstract public class AbstractBasicMaskingOrchestrator extends AbstractDefaultMaskingOrchestrator implements MaskingOrchestrator {

    /**
     * Return the StorageDevice.
     *
     * @return
     */
    public abstract BlockStorageDevice getDevice();

    /**
     * Creation of steps will depend on the device type. So, it is the
     * responsibility of sub classes to implement.
     *
     * @param workflow
     * @param zoningStep
     * @param device
     * @param storage
     * @param exportGroup
     * @param initiatorURIs
     * @param volumeMap
     * @param token
     * @param zoningStepNeeded Determine whether zone step is needed
     * @return
     * @throws Exception
     */
    public boolean determineExportGroupCreateSteps(Workflow workflow, String zoningStep,
            BlockStorageDevice device, StorageSystem storage, ExportGroup exportGroup,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, boolean zoningStepNeeded, String token) throws Exception {
        return false;
    }

    /**
     * Generates snapshot related workflow steps.
     *
     * @param workflow
     * @param storage
     * @param previousStep
     * @param volumeMap
     * @param values
     * @return
     */
    public String checkForSnapshotsToCopyToTarget(Workflow workflow,
            StorageSystem storage, String previousStep, Map<URI, Integer> volumeMap,
            Collection<Map<URI, Integer>> values) {
        return null;
    }

    /**
     * Create storage level masking components to support the requested
     * ExportGroup object. This operation will be flexible enough to take into
     * account initiators that are in some already existent in some
     * StorageGroup. In such a case, the underlying masking component will be
     * "adopted" by the ExportGroup. Further operations against the "adopted"
     * mask will only allow for addition and removal of those initiators/volumes
     * that were added by a Bourne request. Existing initiators/volumes will be
     * maintained.
     *
     *
     * @param storageURI - URI referencing underlying storage array
     * @param exportGroupURI - URI referencing Bourne-level masking, ExportGroup
     * @param initiatorURIs - List of Initiator URIs
     * @param volumeMap - Map of Volume URIs to requested Integer URI
     * @param token - Identifier for operation
     * @throws Exception
     */
    @Override
    public void exportGroupCreate(URI storageURI, URI exportGroupURI, List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = null;
        try {
            BlockStorageDevice device = getDevice();
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
            logExportGroup(exportGroup, storageURI);

            if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                _log.info("export_create: initiator list non-empty");

                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate", true, token);

                // Create two steps, one for Zoning, one for the ExportGroup actions.
                // This step is for zoning. It is not specific to a single
                // NetworkSystem, as it will look at all the initiators and targets and compute
                // the zones required (which might be on multiple NetworkSystems.)
                String zoningStep = generateDeviceSpecificZoningCreateWorkflow(workflow, null, exportGroup,
                        null, volumeMap);

                boolean createdSteps = determineExportGroupCreateSteps(workflow, zoningStep, device, storage, exportGroup,
                        initiatorURIs, volumeMap, false, token);

                if (createdSteps) {
                    // Execute the plan and allow the WorkflowExecutor to fire the
                    // taskCompleter.
                    String successMessage = String.format(
                            "ExportGroup successfully applied for StorageArray %s", storage.getLabel());
                    workflow.executePlan(taskCompleter, successMessage);
                } else {
                    _log.info("export_create: no steps created");
                    taskCompleter.ready(_dbClient);
                }
            } else {
                _log.info("export_create: initiator list empty, no steps to create");
                taskCompleter.ready(_dbClient);
            }
        } catch (DeviceControllerException dex) {
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, DeviceControllerException.errors
                        .vmaxExportGroupCreateError(dex.getMessage()));
            }
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            // TODO add service code here
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(ex.getMessage(), ex);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    @Override
    public void exportGroupAddInitiators(URI storageURI, URI exportGroupURI,
            List<URI> initiatorURIs, String token) throws Exception {
        BlockStorageDevice device = getDevice();
        String previousStep = null;
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
        logExportGroup(exportGroup, storageURI);
        // Set up workflow steps.
        Workflow workflow = _workflowService.getNewWorkflow(
                MaskingWorkflowEntryPoints.getInstance(), "exportGroupAddInitiators", true, token);
        Map<URI, List<URI>> zoneMasksToInitiatorsURIs = new HashMap<URI, List<URI>>();
        Map<URI, Map<URI, Integer>> zoneNewMasksToVolumeMap = new HashMap<URI, Map<URI, Integer>>();
        Map<URI, ExportMask> refreshedMasks = new HashMap<URI, ExportMask>();

        List<URI> hostURIs = new ArrayList<URI>();
        Map<String, URI> portNameToInitiatorURI = new HashMap<String, URI>();
        List<String> portNames = new ArrayList<String>();
        // Populate the port WWN/IQNs (portNames) and the
        // mapping of the WWN/IQNs to Initiator URIs
        processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI, hostURIs);

        // Populate a map of volumes on the storage device
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        if (exportGroup.getVolumes() != null) {
            for (Map.Entry<String, String> entry : exportGroup.getVolumes().entrySet()) {
                URI boURI = URI.create(entry.getKey());
                Integer hlu = Integer.valueOf(entry.getValue());
                BlockObject bo = BlockObject.fetch(_dbClient, boURI);
                if (bo.getStorageController().equals(storageURI)) {
                    volumeMap.put(boURI, hlu);
                    blockObjects.add(bo);
                }
            }
        }

        // We always want to have the full list of initiators for the hosts involved in
        // this export. This will allow the export operation to always find any
        // existing exports for a given host.
        queryHostInitiatorsAndAddToList(portNames, portNameToInitiatorURI,
                initiatorURIs, hostURIs);

        boolean anyOperationsToDo = false;
        Map<String, Set<URI>> matchingExportMaskURIs = device.findExportMasks(storage, portNames, false);
        if (matchingExportMaskURIs != null && !matchingExportMaskURIs.isEmpty()) {
            // There were some exports out there that already have some or all of the
            // initiators that we are attempting to add. We need to only add
            // volumes to those existing exports.
            List<URI> initiatorURIsCopy = new ArrayList<URI>();
            initiatorURIsCopy.addAll(initiatorURIs);

            // This loop will determine a list of volumes to update per export mask
            Map<URI, Map<URI, Integer>> existingMasksToUpdateWithNewVolumes = new HashMap<URI, Map<URI, Integer>>();
            Map<URI, Set<Initiator>> existingMasksToUpdateWithNewInitiators = new HashMap<URI, Set<Initiator>>();
            for (Map.Entry<String, Set<URI>> entry : matchingExportMaskURIs.entrySet()) {
                URI initiatorURI = portNameToInitiatorURI.get(entry.getKey());
                Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                initiatorURIsCopy.remove(initiatorURI);
                // Get a list of the ExportMasks that were matched to the initiator
                List<URI> exportMaskURIs = new ArrayList<URI>();
                exportMaskURIs.addAll(entry.getValue());
                List<ExportMask> masks = _dbClient.queryObject(ExportMask.class, exportMaskURIs);
                _log.info(String.format("initiator %s is in these masks {%s}",
                        initiator.getInitiatorPort(), Joiner.on(',').join(exportMaskURIs)));
                for (ExportMask mask : masks) {
                    // Check for NO_VIPR. If found, avoid this mask.
                    if (mask.getMaskName() != null && mask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
                        _log.info(String.format(
                                "ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it",
                                mask.getMaskName(), ExportUtils.NO_VIPR));
                        continue;
                    }

                    if (!refreshedMasks.containsKey(mask.getId())) {
                        mask = getDevice().refreshExportMask(storage, mask);
                        refreshedMasks.put(mask.getId(), mask);
                    }

                    _log.info(String.format("mask %s has initiator %s", mask.getMaskName(),
                            initiator.getInitiatorPort()));
                    if (!mask.getInactive() && mask.getStorageDevice().equals(storageURI)) {
                        // Loop through all the block objects that have been exported
                        // to the storage system and place only those that are not
                        // already in the masks to the placement list
                        for (BlockObject blockObject : blockObjects) {
                            if (!mask.hasExistingVolume(blockObject.getWWN()) &&
                                    !mask.hasUserAddedVolume(blockObject.getWWN())) {
                                Map<URI, Integer> newVolumesMap = existingMasksToUpdateWithNewVolumes
                                        .get(mask.getId());
                                if (newVolumesMap == null) {
                                    newVolumesMap = new HashMap<URI, Integer>();
                                    existingMasksToUpdateWithNewVolumes.put(mask.getId(),
                                            newVolumesMap);
                                }
                                newVolumesMap.put(blockObject.getId(),
                                        volumeMap.get(blockObject.getId()));
                            }
                        }

                        // Let's try to hunt down any additional initiators in this update that need to be added to
                        // existing masks because they belong to the same hosts.
                        //
                        // We're still OK if the mask contains ONLY initiators that can be found
                        // in our export group, because we would simply add to them.
                        if (mask.getInitiators() != null) {
                            for (String existingMaskInitiatorStr : mask.getInitiators()) {

                                // Now look at it from a different angle. Which one of our export group initiators
                                // are NOT in the current mask? And if so, if it belongs to the same host as an existing one,
                                // we should add it to this mask.
                                Iterator<URI> initiatorIter = initiatorURIsCopy.iterator();
                                while (initiatorIter.hasNext()) {
                                    Initiator initiatorCopy = _dbClient.queryObject(Initiator.class, initiatorIter.next());

                                    if (!mask.hasInitiator(initiatorCopy.getId().toString())) {
                                        Initiator existingMaskInitiator = _dbClient.queryObject(Initiator.class,
                                                URI.create(existingMaskInitiatorStr));
                                        if (initiatorCopy.getHost().equals(existingMaskInitiator.getHost())) {
                                            // Add to the list of initiators we need to add to this mask
                                            Set<Initiator> existingMaskInitiators = existingMasksToUpdateWithNewInitiators
                                                    .get(mask.getId());
                                            if (existingMaskInitiators == null) {
                                                existingMaskInitiators = new HashSet<Initiator>();
                                                existingMasksToUpdateWithNewInitiators.put(mask.getId(), existingMaskInitiators);
                                            }
                                            if (!existingMaskInitiators.contains(initiatorCopy)) {
                                                existingMaskInitiators.add(initiatorCopy);
                                            }
                                            initiatorIter.remove(); // remove this from the list of initiators we'll make a new mask from
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Update the list of volumes and initiators for the mask
                    Map<URI, Integer> volumeMapForExistingMask = existingMasksToUpdateWithNewVolumes
                            .get(mask.getId());
                    if (volumeMapForExistingMask != null && !volumeMapForExistingMask.isEmpty()) {
                        mask.addVolumes(volumeMapForExistingMask);
                    }

                    Set<Initiator> initiatorSetForExistingMask = existingMasksToUpdateWithNewInitiators
                            .get(mask.getId());
                    if (initiatorSetForExistingMask != null && !initiatorSetForExistingMask.isEmpty()) {
                        mask.addInitiators(initiatorSetForExistingMask);
                    }

                    updateZoningMap(exportGroup, mask);
                    _dbClient.updateAndReindexObject(mask);
                    // TODO: All export group modifications should be moved to completers
                    exportGroup.addExportMask(mask.getId());
                    _dbClient.updateAndReindexObject(exportGroup);
                }
            }

            // The initiatorURIsCopy was used in the foreach initiator loop to see
            // which initiators already exist in a mask. If it is non-empty,
            // then it means there are initiators that are new,
            // so let's add them to the main tracker
            Map<URI, List<URI>> hostInitiatorMap = new HashMap<URI, List<URI>>();
            if (!initiatorURIsCopy.isEmpty()) {
                for (URI newExportMaskInitiator : initiatorURIsCopy) {

                    Initiator initiator = _dbClient.queryObject(Initiator.class, newExportMaskInitiator);
                    List<URI> initiatorSet = hostInitiatorMap.get(initiator.getHost());
                    if (initiatorSet == null) {
                        initiatorSet = new ArrayList<URI>();
                        hostInitiatorMap.put(initiator.getHost(), initiatorSet);
                    }
                    initiatorSet.add(initiator.getId());

                    _log.info(String.format("host = %s, "
                            + "initiators to add: %d, "
                            + "existingMasksToUpdateWithNewVolumes.size = %d",
                            initiator.getHost(),
                            hostInitiatorMap.get(initiator.getHost()).size(),
                            existingMasksToUpdateWithNewVolumes.size()));
                }
            }

            for (URI host : hostInitiatorMap.keySet()) {
                // Create two steps, one for Zoning, one for the ExportGroup actions.
                // This step is for zoning. It is not specific to a single NetworkSystem,
                // as it will look at all the initiators and targets and compute the
                // zones required (which might be on multiple NetworkSystems.)
                GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, previousStep, storage, exportGroup,
                        hostInitiatorMap.get(host), volumeMap, token);
                previousStep = result.getStepId();
                zoneNewMasksToVolumeMap.put(result.getMaskURI(), volumeMap);
                anyOperationsToDo = true;
                previousStep = result.getStepId();
            }

            _log.info(String.format("existingMasksToUpdateWithNewVolumes.size = %d",
                    existingMasksToUpdateWithNewVolumes.size()));

            _log.info(String.format("existingMasksToUpdateWithNewInitiators.size = %d",
                    existingMasksToUpdateWithNewInitiators.size()));

            previousStep = checkForSnapshotsToCopyToTarget(workflow, storage, previousStep,
                    volumeMap, existingMasksToUpdateWithNewVolumes.values());

            // At this point we have a mapping of all the masks that we need to update with new volumes
            // stepMap [URI, String] => [Export Mask URI, StepId of previous task i.e. Add volumes work flow.]
            for (Map.Entry<URI, Map<URI, Integer>> entry : existingMasksToUpdateWithNewVolumes
                    .entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                Map<URI, Integer> volumesToAdd = entry.getValue();
                _log.info(String.format("adding these volumes %s to mask %s",
                        Joiner.on(",").join(volumesToAdd.keySet()), mask.getMaskName()));
                List<URI> volumeURIs = new ArrayList<URI>();
                volumeURIs.addAll(volumesToAdd.keySet());
                previousStep = generateDeviceSpecificAddVolumeWorkFlow(workflow,
                        previousStep, storage, exportGroup, mask,
                        volumesToAdd, volumeURIs, null);
                anyOperationsToDo = true;
            }

            // At this point we have a mapping of all the masks that we need to update with new initiators
            for (Entry<URI, Set<Initiator>> entry : existingMasksToUpdateWithNewInitiators
                    .entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                Set<Initiator> initiatorsToAdd = entry.getValue();
                List<URI> initiatorsURIs = new ArrayList<URI>();
                for (Initiator initiator : initiatorsToAdd) {
                    initiatorsURIs.add(initiator.getId());
                }
                _log.info(String.format("adding these initiators %s to mask %s", Joiner
                        .on(",").join(initiatorsURIs), mask.getMaskName()));
                Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
                maskToInitiatorsMap.put(mask.getId(), initiatorURIs);

                previousStep = generateDeviceSpecificAddInitiatorWorkFlow(workflow, previousStep,
                        storage, exportGroup, mask, null, initiatorsURIs,
                        maskToInitiatorsMap, token);

                anyOperationsToDo = true;
            }

        } else {
            // None of the initiators that we're trying to add exist on the
            // array in some export. We need to find the ExportMask that was created by
            // the system and add the new initiator(s) to it.
            boolean foundASystemCreatedMask = false;
            Map<String, List<URI>> hostInitiatorMap = new HashMap<String, List<URI>>();
            if (!initiatorURIs.isEmpty()) {
                for (URI newExportMaskInitiator : initiatorURIs) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, newExportMaskInitiator);
                    if (initiator != null) {
                        String hostURIString = initiator.getHost().toString();
                        List<URI> initiatorSet = hostInitiatorMap.get(hostURIString);
                        if (initiatorSet == null) {
                            hostInitiatorMap.put(initiator.getHost().toString(),
                                    new ArrayList<URI>());
                            initiatorSet = hostInitiatorMap.get(hostURIString);
                        }
                        initiatorSet.add(initiator.getId());

                        _log.info(String.format("host = %s, "
                                + "initiators to add: %d, ",
                                initiator.getHost(),
                                hostInitiatorMap.get(hostURIString).size()));
                    }
                }
            }

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup);
            if (!exportMasks.isEmpty()) {
                _log.info("There are export masks for this group. Adding initiators.");
                // Loop through all the exports and add the initiators to those masks on
                // the storage system that were created by Bourne and are still active.
                for (ExportMask exportMask : exportMasks) {                   
                    if (exportMask != null && !exportMask.getInactive()
                            && exportMask.getStorageDevice().equals(storageURI)
                            && exportMask.getCreatedBySystem()) {
                        List<URI> newInitiators = hostInitiatorMap.get(exportMask.getResource());
                        if (newInitiators != null && !newInitiators.isEmpty()) {
                            zoneMasksToInitiatorsURIs.put(exportMask.getId(), newInitiators);

                            previousStep = generateDeviceSpecificExportMaskAddInitiatorsWorkflow(workflow, previousStep, storage,
                                    exportGroup, exportMask, null, newInitiators, token);
                            foundASystemCreatedMask = true;
                            anyOperationsToDo = true;
                        }
                    }
                }
            }

            if (!foundASystemCreatedMask) {
                List<String> volumeURIsWithoutHLUs = ExportUtils.findVolumesWithoutHLUs(exportGroup);
                if (!volumeURIsWithoutHLUs.isEmpty()) {
                    // COP-16874. The following situation can happen if the ExportMasks are ingested.
                    // There are volumes in the ExportGroup that do not have an HLU assigned.
                    // For each ExportMask in the ExportGroup, get the Volume to HLU mapping, then reconcile the
                    // HLUs that are in the ExportGroup. Also take the discovered HLU mapping and update
                    // volumeMap so that it's populated with proper HLUs. This way new nodes that are added
                    // will get consistent HLUs applied for their volumes
                    for (ExportMask exportMask : ExportMaskUtils.getExportMasks(_dbClient, exportGroup)) {
                        Map<URI, Integer> refreshedVolumeMap = device.getExportMaskHLUs(storage, exportMask);
                        if (!refreshedVolumeMap.isEmpty()) {
                            ExportUtils.reconcileHLUs(_dbClient, exportGroup, exportMask, volumeMap);
                            _dbClient.persistObject(exportGroup);
                            for (URI uri : refreshedVolumeMap.keySet()) {
                                Integer hlu = refreshedVolumeMap.get(uri);
                                if (volumeMap.containsKey(uri)) {
                                    volumeMap.put(uri, hlu);
                                }
                            }
                            _log.info(String.format("ExportMask %s (%s) will be updated with these volumes %s", exportMask.getMaskName(),
                                    exportMask.getId(), CommonTransformerFunctions.collectionString(volumeMap.entrySet())));
                            break; // Do the reconciliation once, based on the first non-empty refreshedVolumeMap that's found
                        }
                    }
                }
                _log.info("There are no masks for this export. Need to create anew.");

                for (String host : hostInitiatorMap.keySet()) {
                    // Zoning is done for the new masks identified i.e. zoneNewMasksToVolumeMap.
                    GenExportMaskCreateWorkflowResult result = generateDeviceSpecificExportMaskCreateWorkFlow(workflow, previousStep,
                            storage, exportGroup,
                            hostInitiatorMap.get(host), volumeMap, token);
                    previousStep = result.getStepId();
                    zoneNewMasksToVolumeMap.put(result.getMaskURI(), volumeMap);
                    anyOperationsToDo = true;
                }
            }
        }

        if (anyOperationsToDo) {
            if (!zoneNewMasksToVolumeMap.isEmpty()) {
                List<URI> exportMaskList = new ArrayList<URI>();
                exportMaskList.addAll(zoneNewMasksToVolumeMap.keySet());
                Map<URI, Integer> overallVolumeMap = new HashMap<URI, Integer>();
                for (Map<URI, Integer> oneVolumeMap : zoneNewMasksToVolumeMap.values()) {
                    overallVolumeMap.putAll(oneVolumeMap);
                }
                previousStep = generateDeviceSpecificZoningCreateWorkflow(workflow, previousStep, exportGroup,
                        exportMaskList, overallVolumeMap);
            }
            if (!zoneMasksToInitiatorsURIs.isEmpty()) {
                previousStep = generateDeviceSpecificZoningAddInitiatorsWorkflow(workflow, previousStep, exportGroup,
                        zoneMasksToInitiatorsURIs);
            }
            String successMessage = String.format(
                    "Successfully exported to initiators on StorageArray %s", storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);
        } else {
            _log.info("There were no operations to perform.  Mask may already be in desired state.");
            taskCompleter.ready(_dbClient);
        }
    }

    @Override
    public void exportGroupRemoveInitiators(URI storageURI, URI exportGroupURI,
            List<URI> initiatorURIs, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = null;
        BlockStorageDevice device = getDevice();
        taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
        StringBuffer errorMessage = new StringBuffer();
        logExportGroup(exportGroup, storageURI);
        // Set up workflow steps.
        Workflow workflow = _workflowService.getNewWorkflow(
                MaskingWorkflowEntryPoints.getInstance(), "exportGroupRemoveInitiators", true,
                token);

        Initiator firstInitiator = _dbClient.queryObject(Initiator.class, initiatorURIs.get(0));
        // No need to validate the orchestrator level validation for vplex/rp. Hence ignoring validation for vplex/rp initiators.
        boolean isValidationNeeded = validatorConfig.isValidationEnabled()
                && !VPlexControllerUtils.isVplexInitiator(firstInitiator, _dbClient)
                && !ExportUtils.checkIfInitiatorsForRP(Arrays.asList(firstInitiator));
        _log.info("Orchestration level validation needed : {}", isValidationNeeded);

        Map<String, URI> portNameToInitiatorURI = new HashMap<String, URI>();
        List<String> portNames = new ArrayList<String>();
        // Populate the port WWN/IQNs (portNames) and the
        // mapping of the WWN/IQNs to Initiator URIs
        processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI);

        // Populate a map of volumes on the storage device associated with this ExportGroup
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        if (exportGroup.getVolumes() != null) {
            for (Map.Entry<String, String> entry : exportGroup.getVolumes().entrySet()) {
                URI boURI = URI.create(entry.getKey());
                BlockObject bo = BlockObject.fetch(_dbClient, boURI);
                if (bo.getStorageController().equals(storageURI)) {
                    blockObjects.add(bo);
                }
            }
        }

        List<String> initiatorNames = new ArrayList<String>();

        for (URI initiatorURI : initiatorURIs) {
            Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
            String normalizedName = Initiator.normalizePort(initiator.getInitiatorPort());
            initiatorNames.add(normalizedName);
        }
        _log.info("Normalized initiator names :{}", initiatorNames);
        device.findExportMasks(storage, initiatorNames, false);

        Map<URI, Boolean> initiatorIsPartOfFullListFlags = flagInitiatorsThatArePartOfAFullList(exportGroup, initiatorURIs);

        boolean anyOperationsToDo = false;
        if (exportGroup != null && !ExportMaskUtils.getExportMasks(_dbClient, exportGroup).isEmpty()) {
            // There were some exports out there that already have some or all of the
            // initiators that we are attempting to remove. We need to only
            // remove the volumes that the user added to these masks
            Map<String, Set<URI>> matchingExportMaskURIs = getInitiatorToExportMaskMap(exportGroup);

            // This loop will determine a list of volumes to update per export mask
            Map<URI, List<URI>> existingMasksToRemoveInitiator = new HashMap<URI, List<URI>>();
            Map<URI, List<URI>> existingMasksToRemoveVolumes = new HashMap<URI, List<URI>>();
            Map<URI, List<URI>> existingMasksToCoexistInitiators = new HashMap<URI, List<URI>>();
            for (Map.Entry<String, Set<URI>> entry : matchingExportMaskURIs.entrySet()) {
                URI initiatorURI = portNameToInitiatorURI.get(entry.getKey());
                if (initiatorURI == null || !initiatorURIs.contains(initiatorURI)) {
                    // Entry key points to an initiator that was not passed in the remove request
                    continue;
                }
                Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);

                // Get a list of the ExportMasks that were matched to the initiator
                // go through the initiators and figure out the proper intiator and volume ramifications
                // to the existing masks.
                List<URI> exportMaskURIs = new ArrayList<URI>();
                exportMaskURIs.addAll(entry.getValue());
                List<ExportMask> masks = _dbClient.queryObject(ExportMask.class, exportMaskURIs);
                _log.info(String.format("initiator %s masks {%s}", initiator.getInitiatorPort(),
                        Joiner.on(',').join(exportMaskURIs)));
                for (ExportMask mask : masks) {
                    if (mask == null || mask.getInactive() || !mask.getStorageDevice().equals(storageURI)) {
                        continue;
                    }
                    mask = getDevice().refreshExportMask(storage, mask);
                    _log.info(String.format("mask %s has initiator %s", mask.getMaskName(),
                            initiator.getInitiatorPort()));
                    if (mask.getCreatedBySystem()) {
                        // We cannot remove initiator if there are existing volumes in the mask.
                        if (!mask.hasAnyExistingVolumes()) {
                            // If there's more than one export group, that means there's my export group plus another one.
                            // Best to just leave that initiator alone.
                            Set<URI> exportGroupURIs = new HashSet<URI>();
                            if (ExportUtils.isExportMaskShared(_dbClient, mask.getId(), exportGroupURIs)) {
                                // Need to do another check against the initiator. If the initiator is not in any of
                                // the other ExportGroups, then we can remove it.
                                exportGroupURIs.remove(exportGroupURI);
                                if (ExportUtils.checkIfAnyExportGroupsContainInitiator(_dbClient, exportGroupURIs, initiator)) {
                                    _log.info(String.format(
                                            "Initiator %s is in an ExportMask that is shared by ExportGroups %s, so we will not remove it",
                                            initiator.getInitiatorPort(), Joiner.on(',').join(exportGroupURIs)));
                                } else {
                                    _log.info(String.format("Initiator %s is in an ExportMask that is shared by ExportGroups %s, " +
                                            "but the initiator is not in any of them. Will remove it from the ExportMask.",
                                            initiator.getInitiatorPort(), Joiner.on(',').join(exportGroupURIs)));
                                    List<URI> initiators = existingMasksToRemoveInitiator.get(mask.getId());
                                    if (initiators == null) {
                                        initiators = new ArrayList<URI>();
                                        existingMasksToRemoveInitiator.put(mask.getId(), initiators);
                                    }
                                    if (!initiators.contains(initiator.getId())) {
                                        initiators.add(initiator.getId());
                                    }
                                }
                            } else {
                                _log.info(String.format("We can remove initiator %s from mask %s", initiator.getInitiatorPort(),
                                        mask.getMaskName()));
                                List<URI> initiators = existingMasksToRemoveInitiator.get(mask.getId());
                                if (initiators == null) {
                                    initiators = new ArrayList<URI>();
                                    existingMasksToRemoveInitiator.put(mask.getId(), initiators);
                                }
                                if (!initiators.contains(initiator.getId())) {
                                    initiators.add(initiator.getId());
                                }
                            }

                            // Remove volumes from masks that aren't in our export group if our initiator was involved.
                            // Also check to see if that volume is already in another export group with that initiator.
                            List<URI> volumesToRemove = new ArrayList<URI>();
                            for (String volumeIdStr : exportGroup.getVolumes().keySet()) {
                                URI egVolumeID = URI.create(volumeIdStr);
                                BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, egVolumeID);
                                // Volumes cannot be removed if there are existing initiators in the mask.
                                if (bo != null && mask.getUserAddedVolumes().containsValue(bo.getId().toString())
                                        && !mask.hasAnyExistingInitiators()) {
                                    int exportGroupsWithVolume = ExportUtils.getNumberOfExportGroupsWithVolume(initiator, egVolumeID,
                                            _dbClient);
                                    if (exportGroupsWithVolume > 1) {
                                        _log.info(String
                                                .format("Found that my volume %s is in another export group with this initiator %s, so we shouldn't remove it from the mask",
                                                        volumeIdStr, initiator.getInitiatorPort()));
                                    } else {
                                        // If this initiator is part of the full list of initiators for
                                        // compute resource, then it implies, that we will be removing
                                        // it from the export. In such case, we would need to remove the
                                        // related volumes from the export.
                                        // If the initiator is part of partial list of initiators for
                                        // a compute resource, then we should only bother to remove the
                                        // initiator and not touch the volumes
                                        if (initiatorIsPartOfFullListFlags.get(initiatorURI)) {
                                            _log.info(String.format("We can potentially remove volume %s from mask %s", volumeIdStr,
                                                    mask.getMaskName()));
                                            if (!volumesToRemove.contains(egVolumeID)) {
                                                volumesToRemove.add(egVolumeID);
                                            }
                                        }
                                    }
                                }
                            }

                            // Place the volumes to remove into the map corresponding to the map we're currently processing.
                            if (!volumesToRemove.isEmpty()) {
                                // Only remove volumes from masks as a side-effect of initiator removal for non-initiator export group
                                // types.
                                // Otherwise this logic may remove volumes from masks that have references to other initiators to the same
                                // host.
                                if (!exportGroup.forInitiator()) {
                                    List<URI> removeVolumesList = existingMasksToRemoveVolumes.get(mask.getId());
                                    if (removeVolumesList == null) {
                                        removeVolumesList = new ArrayList<URI>();
                                        existingMasksToRemoveVolumes.put(mask.getId(),
                                                removeVolumesList);
                                    }
                                    removeVolumesList.addAll(volumesToRemove);
                                } else {
                                    // Just a reminder to the world in the case where Initiator is used in this odd situation.
                                    _log.info(
                                            "Removing volumes from an Initiator type export group as part of an initiator removal is not supported.");
                                }
                            }
                        } else {
                            errorMessage.append(String.format("Mask %s is having existing volumes %s", mask.forDisplay(),
                                    Joiner.on(", ").join(mask.getExistingVolumes().keySet())));
                        }
                    } else {
                        // Loop through all the block objects that have been
                        // exported to the storage system and place only those that
                        // are not already in the masks to the remove list
                        for (BlockObject blockObject : blockObjects) {
                            // Volumes cannot be removed if there are existing initiators in the mask
                            if (mask.hasUserCreatedVolume(blockObject.getWWN())) {
                                // If any system-created initiator in the mask is not in our list to remove, then we shouldn't remove
                                // the block object because another initiator in a ViPR export group is depending on that object being
                                // there.
                                //
                                // Once all user-added initiators are slated for removal, the block volume can be removed
                                // as well.

                                // CTRL-8804- Volumes can be removed, if there are no user Added initiators.
                                boolean okToRemove = true;
                                if (mask.getUserAddedInitiators() != null) {
                                    for (URI maskInitiatorId : URIUtil.toURIList(mask.getUserAddedInitiators().values())) {
                                        if (!initiatorURIs.contains(maskInitiatorId)) {
                                            okToRemove = false;
                                            _log.info("Will not remove block object {} because there are initiators " +
                                                    "remaining in the export mask that were created by the system [1]",
                                                    String.valueOf(blockObject.getId()));
                                            break;
                                        }
                                    }
                                }

                                // CTRL-10018 - Volumes can not be removed if any initiators in the mask that AREN'T
                                // being removed are in the export group (or any other export group). If so, those
                                // initiators are still relying on the volume to be there.
                                if (mask.getInitiators() != null && exportGroup.getInitiators() != null) {
                                    for (URI maskInitiatorId : URIUtil.toURIList(mask.getInitiators())) {

                                        // We are only concerned about initiators in the mask that are NOT the ones being removed.
                                        if (!initiatorURIs.contains(maskInitiatorId)) {

                                            // This block will check to see if the export group we're currently referring to
                                            // has any initiators that are still part of the export group, even after removing
                                            // consideration of initiators we are removing from the export group.
                                            if (exportGroup.getInitiators().contains(maskInitiatorId.toString())) {
                                                okToRemove = false;
                                                _log.info("Will not remove block object {} because there are initiators " +
                                                        "remaining in the export mask that were created by the system [2]",
                                                        String.valueOf(blockObject.getId()));
                                                break;
                                            }

                                            // This block will make sure the volumes/initiator combination is not in any other export group.
                                            // This is far less likely to be the case, but we do support overlapping export groups, so this
                                            // check is necessary.
                                            Initiator maskInitiator = _dbClient.queryObject(Initiator.class, maskInitiatorId);
                                            Set<URI> exportGroupURIs = new HashSet<URI>();

                                            // Collect all the export groups that contain this mask
                                            ExportUtils.isExportMaskShared(_dbClient, mask.getId(), exportGroupURIs);

                                            // Remove our export group from the list
                                            exportGroupURIs.remove(exportGroup.getId());

                                            // If there are any other export groups that reference this mask, do they contain the initiator
                                            // and block object as well?
                                            if (!exportGroupURIs.isEmpty()
                                                    && ExportUtils.checkIfAnyExportGroupsContainInitiatorAndBlockObject(_dbClient,
                                                            exportGroupURIs, maskInitiator, blockObject)) {
                                                _log.info(String
                                                        .format("Volume %s and Initiator %s is in an ExportMask that is shared by ExportGroups %s, so we will not remove it",
                                                                blockObject.getId(), initiator.getInitiatorPort(),
                                                                Joiner.on(',').join(exportGroupURIs)));
                                                okToRemove = false;
                                                break;
                                            }
                                        }
                                    }
                                }

                                if (okToRemove) {
                                    List<URI> removeVolumesList = existingMasksToRemoveVolumes
                                            .get(mask.getId());
                                    if (removeVolumesList == null) {
                                        removeVolumesList = new ArrayList<URI>();
                                        existingMasksToRemoveVolumes.put(mask.getId(),
                                                removeVolumesList);
                                    }

                                    if (!removeVolumesList.contains(blockObject.getId())) {
                                        removeVolumesList.add(blockObject.getId());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // At this point we have a mapping of masks to objects that we want to remove

            Set<URI> masksGettingRemoved = new HashSet<URI>();

            // In this loop we are trying to remove those initiators that exist
            // on a mask that ViPR created.
            Map<URI, String> stepMap = new HashMap<URI, String>();
            for (Map.Entry<URI, List<URI>> entry : existingMasksToRemoveInitiator.entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                List<URI> initiatorsToRemove = entry.getValue();

                if (initiatorsToRemove.size() >= ExportUtils.getExportMaskAllInitiators(mask, _dbClient).size()) {
                    masksGettingRemoved.add(mask.getId());
                    // For this case, we are attempting to remove all the
                    // initiators in the mask. This means that we will have to
                    // delete the
                    // exportGroup
                    _log.info(String.format("mask %s has removed all "
                            + "initiators, we are going to delete the mask from the "
                            + "array", mask.getMaskName()));
                    errorMessage.append(String.format("Mask %s will be removed from array", mask.forDisplay()));
                    List<URI> maskVolumeURIs = ExportMaskUtils.getVolumeURIs(mask);
                    List<URI> maskInitiatorURIs = Lists.newArrayList(
                            Collections2.transform(ExportMaskUtils.getInitiatorsForExportMask(_dbClient, mask, null),
                                    CommonTransformerFunctions.fctnDataObjectToID()));
                    stepMap.put(entry.getKey(), generateDeviceSpecificDeleteWorkflow(workflow, null, exportGroup, mask, maskVolumeURIs,
                            maskInitiatorURIs, storage));
                    anyOperationsToDo = true;
                } else {
                    _log.info(String.format("mask %s - going to remove the "
                            + "following initiators %s", mask.getMaskName(),
                            Joiner.on(',').join(initiatorsToRemove)));
                    Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
                    maskToInitiatorsMap.put(mask.getId(), initiatorsToRemove);
                    List<URI> maskVolumeURIs = ExportMaskUtils.getVolumeURIs(mask);
                    stepMap.put(entry.getKey(), generateDeviceSpecificRemoveInitiatorsWorkflow(workflow,
                            null, exportGroup, mask, storage, maskToInitiatorsMap, maskVolumeURIs, initiatorsToRemove, true));
                    anyOperationsToDo = true;
                }



            }

            // In this loop we are trying to remove volumes from masks that
            // ViPR did not create. We have no control over the initiators defined in
            // these masks. We will be removing only those volumes that are applicable
            // for the storage array and ExportGroup.
            for (Map.Entry<URI, List<URI>> entry : existingMasksToRemoveVolumes.entrySet()) {
                if (masksGettingRemoved.contains(entry.getKey())) {
                    _log.info("Mask {} is getting removed, no need to remove volumes from it",
                            entry.getKey().toString());
                    continue;
                }

                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                List<URI> volumesToRemove = entry.getValue();
                List<URI> initiatorsToRemove = existingMasksToRemoveInitiator.get(mask.getId());
                if (initiatorsToRemove != null) {
                    List<URI> initiatorsInExportMask = ExportUtils.getExportMaskAllInitiators(mask, _dbClient);
                    initiatorsInExportMask.removeAll(initiatorsToRemove);
                    if (!initiatorsInExportMask.isEmpty()) {
                        // There are still some initiators in this ExportMask
                        _log.info(String.format("ExportMask %s would have remaining initiators {%s} that require access to {%s}. " +
                                "Not going to remove any of the volumes",
                                mask.getMaskName(), Joiner.on(',').join(initiatorsInExportMask),
                                Joiner.on(',').join(volumesToRemove)));
                        continue;
                    }
                }

                Collection<String> volumesToRemoveURIStrings = Collections2.transform(volumesToRemove,
                        CommonTransformerFunctions.FCTN_URI_TO_STRING);
                List<String> exportMaskVolumeURIStrings = new ArrayList<String>(mask.getVolumes().keySet());
                exportMaskVolumeURIStrings.removeAll(volumesToRemoveURIStrings);

                if (exportMaskVolumeURIStrings.isEmpty() && !mask.hasAnyExistingVolumes()) {
                    _log.info(String.format("All the volumes (%s) from mask %s will be removed, so will have to remove the whole mask",
                            Joiner.on(",").join(volumesToRemove), mask.getMaskName()));
                    errorMessage.append(String.format("Mask %s will be removed from array", mask.forDisplay()));
                    List<URI> maskVolumeURIs = ExportMaskUtils.getVolumeURIs(mask);
                    List<URI> maskInitiatorURIs = Lists.newArrayList(
                            Collections2.transform(ExportMaskUtils.getInitiatorsForExportMask(_dbClient, mask, null),
                                    CommonTransformerFunctions.fctnDataObjectToID()));
                    generateDeviceSpecificDeleteWorkflow(workflow, null, exportGroup, mask, maskVolumeURIs, maskInitiatorURIs, storage);
                    anyOperationsToDo = true;
                } else {
                    // Null taskID is passed in because the generateExportMaskRemoveVolumesWorkflow will fill it in
                    ExportTaskCompleter completer = new ExportRemoveVolumesOnAdoptedMaskCompleter(
                            exportGroupURI, mask.getId(), volumesToRemove, null);
                    _log.info(String.format("A subset of volumes will be removed from mask %s: %s",
                            mask.getMaskName(), Joiner.on(",").join(volumesToRemove)));
                    List<? extends BlockObject> boList = BlockObject.fetchAll(_dbClient, volumesToRemove);
                    errorMessage.append(String.format("A subset of volumes will be removed from mask %s: %s. ",
                            mask.getMaskName(), Joiner.on(", ").join(
                                    Collections2.transform(boList, CommonTransformerFunctions.fctnDataObjectToForDisplay()))));

                    List<URI> maskInitiatorURIs = Lists.newArrayList(
                            Collections2.transform(ExportMaskUtils.getInitiatorsForExportMask(_dbClient, mask, null),
                                    CommonTransformerFunctions.fctnDataObjectToID()));
                    generateDeviceSpecificRemoveVolumesWorkflow(workflow, stepMap.get(entry.getKey()), exportGroup, mask, storage,
                            volumesToRemove, maskInitiatorURIs, completer);

                    anyOperationsToDo = true;


                }
            }

        }

        _log.warn("Error Message {}", errorMessage);

        if (isValidationNeeded && StringUtils.hasText(errorMessage)) {
            throw DeviceControllerException.exceptions.removeInitiatorValidationError(Joiner.on(", ").join(initiatorNames),
                    storage.forDisplay(),
                    errorMessage.toString());
        }

        if (anyOperationsToDo) {
            String successMessage = String.format(
                    "Successfully removed exports for initiators on StorageArray %s",
                    storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);
        } else {
            taskCompleter.ready(_dbClient);
        }
    }

    @Override
    public void exportGroupRemoveVolumes(URI storageURI, URI exportGroupURI, List<URI> volumeURIs,
            String token) throws Exception {
        ExportOrchestrationTask taskCompleter = null;
        try {
            BlockStorageDevice device = getDevice();
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            logExportGroup(exportGroup, storageURI);
            String previousStep = null;
            boolean generatedWorkFlowSteps = false;
            if (!ExportMaskUtils.getExportMasks(_dbClient, exportGroup).isEmpty()) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupRemoveVolumes", true,
                        token);
                List<ExportMask> exportMasksToZoneDelete = new ArrayList<ExportMask>();
                List<ExportMask> exportMasksToZoneRemoveVolumes = new ArrayList<ExportMask>();
                List<ExportMask> exportMasksToDelete = new ArrayList<ExportMask>();
                List<URI> volumesToZoneRemoveVolumes = new ArrayList<URI>();
                List<ExportMask> tempMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup);
                for (ExportMask tempMask : tempMasks) {                   
                    _log.info(String.format("Checking mask %s", tempMask.getMaskName()));
                    if (!tempMask.getInactive()
                            && tempMask.getStorageDevice().equals(storageURI)) {
                        tempMask = device.refreshExportMask(storage, tempMask);
                        // BlockStorageDevice level, so that it has up-to-date
                        // info from the array
                        Set<URI> volumesToRemove = new HashSet<URI>();

                        // If they specify to delete all volumes, we qualify to delete the whole mask.
                        // Otherwise, no chance of us deleting masks as part of this operation because
                        // each mask will still have at least one volume in it.
                        boolean removingLastVolumeFromMask = removingLastExportMaskVolumes(tempMask, new ArrayList<>(volumeURIs));
                        // Initially, we will assume that we should delete the mask if
                        // it looks like we have to remove the ExportMask's last volume
                        boolean deleteEntireMask = removingLastVolumeFromMask;
                        boolean anyVolumesFoundInAnotherExportGroup = false;

                        // Volume removal -- check to see if that volume is already in another export group with that initiator.
                        // check to see if the export mask has other initiators that aren't being removed.
                        for (URI egVolumeID : volumeURIs) {
                            String volumeIdStr = egVolumeID.toString();
                            BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, egVolumeID);
                            if (bo != null && tempMask.hasUserCreatedVolume(bo.getId())) {
                                if (exportGroup.getInitiators() != null) {
                                    for (String initiatorIdStr : exportGroup.getInitiators()) {
                                        if (tempMask.hasInitiator(initiatorIdStr)) {
                                            // In here, we're looking at an initiator that is both in our export group and in the export
                                            // mask we're looking at,
                                            // so it needs further scrutiny. Is this combo in more than one export group? If so, leave it
                                            // alone.
                                            Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorIdStr));

                                            List<ExportGroup> exportGroupList2 = ExportUtils.getInitiatorVolumeExportGroups(initiator,
                                                    egVolumeID, _dbClient);

                                            if (exportGroupList2 != null && exportGroupList2.size() > 1) {
                                                _log.info(String
                                                        .format("Found that my volume %s is in another export group with this initiator %s, so we shouldn't remove it from the mask",
                                                                volumeIdStr, initiator.getInitiatorPort()));
                                                deleteEntireMask = false;
                                                anyVolumesFoundInAnotherExportGroup = true;
                                            } else {
                                                if (!volumesToRemove.contains(egVolumeID)) {
                                                    _log.info(String.format("We can remove volume %s from mask %s", volumeIdStr,
                                                            tempMask.getMaskName()));
                                                    volumesToRemove.add(egVolumeID);
                                                }
                                            }
                                        } else if (tempMask.getCreatedBySystem()) {
                                            _log.info(String.format(
                                                    "Export Mask %s does not contain initiator %s, so we will not modify this export mask",
                                                    tempMask.getId().toString(), initiatorIdStr));
                                        } else {
                                            // We're in a case where there are no user
                                            // added initiators for this *existing*
                                            // mask. So, we should be able remove any
                                            // of the volumes that we added to the
                                            // system.
                                            if (!volumesToRemove.contains((egVolumeID))) {
                                                _log.info(String.format("We can remove volume %s from mask %s", volumeIdStr,
                                                        tempMask.getMaskName()));
                                                volumesToRemove.add(egVolumeID);
                                            }
                                        }
                                    }
                                }
                            } else {
                                _log.info(String
                                        .format("Export mask %s does not contain system-created volume %s, so it will not be removed from this export mask",
                                                tempMask.getId().toString(), volumeIdStr));
                            }
                        }

                        // Determine if we are removing the last volume from the ExportGroup
                        Map<URI, Integer> exportGroupVolumeMap = ExportUtils.getExportGroupVolumeMap(_dbClient, storage, exportGroup);
                        Set<URI> exportGroupVolumeURIs = exportGroupVolumeMap.keySet();
                        exportGroupVolumeURIs.removeAll(volumesToRemove);
                        boolean exportGroupHasMoreVolumes = !exportGroupVolumeURIs.isEmpty();
                        boolean exportMaskIsShared = ExportUtils.isExportMaskShared(_dbClient, tempMask.getId(), null);
                        @SuppressWarnings("unchecked")
                        List<URI> allExportMaskInitiators = ExportUtils.getExportMaskAllInitiators(tempMask, _dbClient);

                        _log.info(String.format("ExportMask %s(%s) - exportGroupHasMoreVolumes=%s exportMaskIsShared=%s " +
                                "removingLastVolumeFromMask=%s anyVolumesFoundInAnotherExportGroup=%s",
                                tempMask.getMaskName(), tempMask.getId(), exportGroupHasMoreVolumes,
                                exportMaskIsShared, removingLastVolumeFromMask, anyVolumesFoundInAnotherExportGroup));

                        boolean canDeleteExportMask = false; // Assume that we cannot delete the ExportMask
                        if (tempMask.getCreatedBySystem()) {
                            // We should only delete ViPR created ExportMasks
                            if (exportMaskIsShared) {
                                // Shared ExportMask, need to evaluate the volumes
                                if (!anyVolumesFoundInAnotherExportGroup && removingLastVolumeFromMask) {
                                    // None of the volumes are being shared by another ExportGroup and
                                    // we're removing the last volume from the ExportMask
                                    canDeleteExportMask = true;
                                    _log.info(String.format("ExportMask %s(%s) - Determined that this mask is shared, " +
                                            "but volumes are exclusive to the ExportGroup %s, so we can delete it",
                                            tempMask.getMaskName(), tempMask.getId(), exportGroup.getId()));
                                }
                            } else if (!anyVolumesFoundInAnotherExportGroup) {
                                // Non-Shared ExportMask, and none of the volumes are shared with another ExportGroup.
                                // Evaluate the situation with the volumes
                                if (!exportGroupHasMoreVolumes && removingLastVolumeFromMask) {
                                    // The remove will empty the ExportGroup or ExportMask of volumes
                                    canDeleteExportMask = true;
                                    _log.info(String
                                            .format("ExportMask %s(%s) - Determined that this mask is not shared and meets the criteria for deletion",
                                                    tempMask.getMaskName(), tempMask.getId()));
                                }
                            } else {
                                _log.info("Checks have determined that the ExportMask %s(%s) should not be deleted",
                                        tempMask.getMaskName(), tempMask.getId());
                            }
                        }

                        if (canDeleteExportMask) {
                            _log.info(String.format("Determined that we can delete mask %s", tempMask.getMaskName()));
                            exportMasksToZoneDelete.add(tempMask);
                            exportMasksToDelete.add(tempMask);
                            generatedWorkFlowSteps = true;
                        } else {
                            // We have determined that we cannot delete the ExportMask. We have to determine if we
                            // should remove initiators or volumes
                            @SuppressWarnings("unchecked")
                            List<URI> userAddedVolumes = (tempMask.getUserAddedVolumes() != null)
                            ? StringSetUtil.stringSetToUriList(tempMask.getUserAddedVolumes().values()) : Collections.EMPTY_LIST;
                            userAddedVolumes.removeAll(volumesToRemove);
                            boolean removingAllUserAddedVolumes = userAddedVolumes.isEmpty();
                            boolean canRemoveVolumes = (!volumesToRemove.isEmpty() && !removingLastVolumeFromMask);

                            _log.info(String
                                    .format("ExportMask %s(%s) - canRemoveVolumes=%s "
                                            +
                                            "allExportMaskInitiators=%d removingLastVolumeFromMask=%s removingAllUserAddedVolumes=%s",
                                            tempMask.getMaskName(), tempMask.getId(),
                                            canRemoveVolumes, allExportMaskInitiators.size(),
                                            removingLastVolumeFromMask, removingAllUserAddedVolumes));

                            if (canRemoveVolumes) {
                                // If we got here it means that:
                                // -- ExportMask was not created by ViPR
                                // -- We're not dealing with a subset of initiators for the ExportMask
                                // Then we will just schedule the removal of the volumes
                                _log.info(String.format(
                                        "Determined that we can remove volumes from mask %s (%s): %s",
                                        tempMask.getMaskName(), tempMask.getId(),
                                        CommonTransformerFunctions.collectionString(volumesToRemove)));
                                exportMasksToZoneRemoveVolumes.add(tempMask);
                                volumesToZoneRemoveVolumes.addAll(volumesToRemove);

                                List<URI> maskInitiatorURIs = Lists.newArrayList(Collections2.transform(
                                        ExportMaskUtils.getInitiatorsForExportMask(_dbClient, tempMask, null),
                                        CommonTransformerFunctions.fctnDataObjectToID()));
                                List<URI> volumesToRemoveList = new ArrayList<>();
                                volumesToRemoveList.addAll(volumesToRemove);
                                previousStep = generateDeviceSpecificExportMaskRemoveVolumesWorkflow(workflow, previousStep, exportGroup,
                                        tempMask, storage, volumesToRemoveList, maskInitiatorURIs, null);
                                generatedWorkFlowSteps = true;
                            }
                        }
                    }
                }
                if (!exportMasksToDelete.isEmpty()) {
                    for (ExportMask exportMaskToDelete : exportMasksToDelete) {
                        _log.info("generating workflow to remove exportmask {}", exportMaskToDelete.getMaskName());
                        List<URI> maskVolumeURIs = ExportMaskUtils.getVolumeURIs(exportMaskToDelete);
                        List<URI> maskInitiatorURIs = Lists.newArrayList(
                                Collections2.transform(ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMaskToDelete, null),
                                        CommonTransformerFunctions.fctnDataObjectToID()));
                        previousStep = generateDeviceSpecificExportMaskDeleteWorkflow(workflow, previousStep, exportGroup,
                                exportMaskToDelete, maskVolumeURIs, maskInitiatorURIs, storage);
                    }
                }

                if (!exportMasksToZoneRemoveVolumes.isEmpty()) {
                    _log.info("generating workflow for exportmask to zoneRemoveVolumes.");
                    // Remove all the indicated volumes from the indicated export masks.
                    previousStep = generateDeviceSpecificZoningRemoveVolumesWorkflow(workflow, previousStep,
                            exportGroup, exportMasksToZoneRemoveVolumes, volumesToZoneRemoveVolumes);

                }

                if (!exportMasksToZoneDelete.isEmpty()) {
                    _log.info("generating workflow to remove all zones in exportmask");
                    // Add the zone ExportMask delete operations
                    previousStep = generateDeviceSpecificZoningDeleteWorkflow(workflow, previousStep,
                            exportGroup, exportMasksToZoneDelete);
                }

                if (generatedWorkFlowSteps) {
                    // Add a task to clean up the export group when the export masks remove their volumes
                    previousStep = generateDeviceSpecificExportGroupRemoveVolumesCleanup(workflow, previousStep, storage, exportGroup,
                            volumeURIs, null);
                }

                String successMessage = String.format(
                        "Successfully removed volumes from export on StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            }
            if (!generatedWorkFlowSteps) {
                taskCompleter.ready(_dbClient);
            }

        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            // TODO add service code here
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(ex.getMessage(), ex);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    @Override
    public void exportGroupDelete(URI storageURI, URI exportGroupURI, String token)
            throws Exception {
        ExportOrchestrationTask taskCompleter = null;
        try {
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            String previousStep = null;
            boolean someOperationDone = false;
            logExportGroup(exportGroup, storageURI);
            if (!ExportMaskUtils.getExportMasks(_dbClient, exportGroup).isEmpty() && !exportGroup.getInactive()) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupDelete", true, token);
                List<ExportMask> exportMasksToZoneDelete = new ArrayList<ExportMask>();
                List<ExportMask> exportMasksToZoneRemoveVolumes = new ArrayList<ExportMask>();
                Set<URI> volumesToZoneRemoveVolumes = new HashSet<URI>();
                List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup);
                for (ExportMask exportMask : exportMasks) {                 
                    taskCompleter.setMask(exportMask.getId());
                    _log.info(String.format("Checking mask %s", exportMask.getMaskName()));
                    if (!exportMask.getInactive()
                            && exportMask.getStorageDevice().equals(storageURI)) {
                        exportMask = getDevice().refreshExportMask(storage, exportMask);

                        Collection<URI> volumeURIs = Collections2.transform(exportGroup.getVolumes().keySet(),
                                CommonTransformerFunctions.FCTN_STRING_TO_URI);

                        // One way to know if we should delete the mask is if all of the volumes in the mask
                        // are represented in the export group.
                        boolean deleteEntireMask = removingLastExportMaskVolumes(exportMask, new ArrayList<>(volumeURIs));
                        _log.info("deleteEntireMask for {}? {}", exportMask.getId(), deleteEntireMask);

                        Set<URI> volumesToRemove = new HashSet<>();
                        if (exportGroup.getInitiators() != null && !exportGroup.getInitiators().isEmpty()) {
                            Set<String> egInitiators = new HashSet<String>(exportGroup.getInitiators());
                            for (String initiatorIdStr : egInitiators) {
                                Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorIdStr));

                                if (initiator == null) {
                                    _log.warn("Found that initiator " + initiatorIdStr
                                            + " in the export group is no longer in the database, removing from the initiator list.");
                                    exportGroup.removeInitiator(URI.create(initiatorIdStr));
                                    _dbClient.updateAndReindexObject(exportGroup);
                                    continue;
                                }

                                // Search for this initiator in another export group
                                List<ExportGroup> exportGroupList = ExportUtils.getInitiatorExportGroups(initiator, _dbClient);
                                // We cannot remove initiator from mask if the mask has existing volumes
                                if (exportMask.hasUserInitiator(URI.create(initiatorIdStr)) && !exportMask.hasAnyExistingVolumes()) {
                                    // If there's more than one export group, that means there's my export group plus another one. (at
                                    // least)
                                    // Best to just leave that initiator alone.
                                    if ((exportGroupList != null && exportGroupList.size() > 1)
                                            && ExportUtils.isExportMaskShared(_dbClient, exportMask.getId(), null)) {
                                        _log.info(String
                                                .format("Found that my initiator is in %s more export groups, so we shouldn't remove it from the mask",
                                                        exportGroupList.size() - 1));
                                        deleteEntireMask = false;
                                    }
                                }
                            }
                        }

                        if (deleteEntireMask) {
                            _log.info(String.format(
                                    "export_delete: export mask %s was either created by system or last volume is being removed.",
                                    exportMask.getMaskName()));
                            exportMasksToZoneDelete.add(exportMask);
                            someOperationDone = true;
                        } else {
                            // Volume removal -- check to see if that volume is already in another export group with that initiator.
                            for (String volumeIdStr : exportGroup.getVolumes().keySet()) {
                                URI egVolumeID = URI.create(volumeIdStr);
                                BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, egVolumeID);
                                if (bo != null && exportMask.hasUserCreatedVolume(bo.getId())) {
                                    if (exportGroup.getInitiators() != null) {
                                        for (String initiatorIdStr : exportGroup.getInitiators()) {
                                            if (exportMask.hasInitiator(initiatorIdStr)) {
                                                Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorIdStr));
                                                List<ExportGroup> exportGroupList2 = ExportUtils.getInitiatorVolumeExportGroups(initiator,
                                                        egVolumeID, _dbClient);

                                                if (exportGroupList2 != null && exportGroupList2.size() > 1) {
                                                    _log.info(String
                                                            .format("Found that my volume %s is in another export group with this initiator %s, so we shouldn't remove it from the mask",
                                                                    volumeIdStr, initiator.getInitiatorPort()));
                                                } else {
                                                    _log.info(String.format("We can remove volume %s from mask %s", volumeIdStr,
                                                            exportMask.getMaskName()));
                                                    volumesToRemove.add(egVolumeID);
                                                }
                                            } else if (exportMask.getCreatedBySystem()) {
                                                _log.info(String
                                                        .format("Export Mask %s does not contain initiator %s, so we will not modify this export mask",
                                                                exportMask.getId().toString(), initiatorIdStr));
                                            } else {
                                                // We're in a case where there are no user added initiators for this *existing* mask. So, we
                                                // should be able remove any
                                                // of the volumes that we added to the system.
                                                volumesToRemove.add(egVolumeID);
                                            }
                                        }
                                    }
                                }
                            }

                            // Remove volume steps are generated based on the initiators we collected for removal.
                            if (!volumesToRemove.isEmpty()) {
                                _log.info(String.format("Mask %s, Removing volumes %s only", exportMask.getMaskName(),
                                        Joiner.on(',').join(volumesToRemove)));
                                _log.info(String.format("volumes in mask: %s", Joiner.on(',').join(exportMask.getVolumes().entrySet())));
                                exportMasksToZoneRemoveVolumes.add(exportMask);
                                volumesToZoneRemoveVolumes.addAll(volumesToRemove);
                                List<URI> maskInitiatorURIs = Lists.newArrayList(Collections2.transform(
                                        ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null),
                                        CommonTransformerFunctions.fctnDataObjectToID()));
                                previousStep = generateDeviceSpecificRemoveVolumesWorkflow(workflow, previousStep,
                                        exportGroup, exportMask, storage, new ArrayList<URI>(volumesToRemove), maskInitiatorURIs, null);

                                someOperationDone = true;
                            }

                        }

                    }
                }
                if (!exportMasksToZoneDelete.isEmpty()) {
                    for (ExportMask exportMask : exportMasksToZoneDelete) {
                        List<URI> volumeURIs = ExportMaskUtils.getVolumeURIs(exportMask);
                        List<URI> maskInitiatorURIs = Lists.newArrayList(
                                Collections2.transform(ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null),
                                        CommonTransformerFunctions.fctnDataObjectToID()));
                        previousStep = generateDeviceSpecificExportMaskDeleteWorkflow(workflow, previousStep, exportGroup,
                                exportMask, volumeURIs, maskInitiatorURIs, storage);
                    }
                    // CTRL-8506 - VNX StorageGroup cannot be deleted because of a race condition with
                    // the zoning. This is a live host test case. So, some initiators are still logged
                    // in by the time ViPR tries to delete the StorageGroup.
                    // General Solution:
                    // When we have to delete ExportMask, we'll un-zone first so that any initiators
                    // that are possibly logged into the array get a chance to log out. That way, there
                    // should not be any problems with removing the ExportMask off the array.
                    //
                    // COP-24183: Reversing the order with serialization to prevent DU if mask validation fails.
                    previousStep = generateDeviceSpecificZoningDeleteWorkflow(workflow, previousStep, exportGroup,
                            exportMasksToZoneDelete);
                }
                if (!exportMasksToZoneRemoveVolumes.isEmpty()) {
                    // Remove all the indicated volumes from the indicated
                    // export masks.
                    generateDeviceSpecificZoningRemoveVolumesWorkflow(workflow, previousStep,
                            exportGroup, exportMasksToZoneRemoveVolumes,
                            new ArrayList<URI>(volumesToZoneRemoveVolumes));
                }

                String successMessage = String.format(
                        "Successfully removed export on StorageArray %s", storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            }

            if (!someOperationDone) {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            // TODO add service code here
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(ex.getMessage(), ex);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    @Override
    public void exportGroupUpdate(URI storageURI, URI exportGroupURI,
            Workflow storageWorkflow, String token) throws Exception {
        TaskCompleter taskCompleter = null;
        try {
            _log.info(String.format("exportGroupUpdate start - Array: %s ExportGroup: %s",
                    storageURI.toString(), exportGroupURI.toString()));
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
            String successMessage = String.format(
                    "ExportGroup %s successfully updated for StorageArray %s",
                    exportGroup.getLabel(), storage.getLabel());
            storageWorkflow.setService(_workflowService);
            storageWorkflow.executePlan(taskCompleter, successMessage);
        } catch (Exception ex) {
            _log.error("ExportGroupUpdate Orchestration failed.", ex);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(ex.getMessage(), ex);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportGroupUpdateFailed(ex);
            }
        }
    }

    @Override
    public void exportGroupChangePathParams(URI storageURI, URI exportGroupURI,
            URI volumeURI, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        ExportPathUpdater updater = new ExportPathUpdater(_dbClient);
        try {
            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(),
                    "exportGroupChangePathParams", true, token);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class,
                    storageURI);
            BlockObject volume = BlockObject.fetch(_dbClient, volumeURI);
            _log.info(String.format("Changing path parameters for volume %s (%s)",
                    volume.getLabel(), volume.getId()));

            // Call the ExportPathUpdater to generate Workflow steps necessary to change
            // the path parameters. It will analyze the ExportGroups versus the ExportParams in
            // the VPool of the volume, and call increaseMaxPaths if necessary.
            updater.generateExportGroupChangePathParamsWorkflow(workflow, _blockScheduler, this,
                    storage, exportGroup, volume, token);

            if (!workflow.getAllStepStatus().isEmpty()) {
                _log.info("The changePathParams workflow has {} steps. Starting the workflow.",
                        workflow.getAllStepStatus().size());
                workflow.executePlan(taskCompleter, "Update the export group on all export masks successfully.");
            } else {
                taskCompleter.ready(_dbClient);
            }

        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(ex.getMessage(), ex);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportGroupCreateFailed(ex);
            }
        }
    }

    @Override
    public void increaseMaxPaths(Workflow workflow, StorageSystem storageSystem,
            ExportGroup exportGroup, ExportMask exportMask, List<URI> newInitiators, String token)
                    throws Exception {
        // Increases the MaxPaths for a given ExportMask if it has Initiators that are not
        // currently zoned to ports. The method generateExportMaskAddInitiatorsWorkflow will
        // allocate additional ports for the newInitiators to be processed.
        // These will be zoned and then subsequently added to the MaskingView / ExportMask.
        Map<URI, List<URI>> zoneMasksToInitiatorsURIs = new HashMap<URI, List<URI>>();
        zoneMasksToInitiatorsURIs.put(exportMask.getId(), newInitiators);
        String zoningStep = generateZoningAddInitiatorsWorkflow(workflow, null,
                exportGroup, zoneMasksToInitiatorsURIs);
        Set<URI> volumeURIs = new HashSet<URI>(StringSetUtil.stringSetToUriList(exportMask.getUserAddedVolumes().values()));
        generateExportMaskAddInitiatorsWorkflow(workflow, zoningStep, storageSystem,
                exportGroup, exportMask, newInitiators, volumeURIs, token);
    }

    public void exportGroupChangePolicyAndLimits(URI storageURI,
            URI exportMaskURI, URI exportGroupURI, List<URI> volumeURIs,
            URI newVpoolURI, boolean rollback, String token) throws Exception {
        // supported only for VMAX and VNX Block.
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    public void changeAutoTieringPolicy(URI storageURI, List<URI> volumeURIs,
            URI newVpoolURI, boolean rollback, String token) throws Exception {
        // supported only for VMAX3 Block.
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

}
