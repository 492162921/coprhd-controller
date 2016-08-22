/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.UnManagedVolumeService;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BaseIngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class IngestVolumesUnexportedSchedulingThread implements Runnable {

    private static final Logger _logger = LoggerFactory.getLogger(IngestVolumesUnexportedSchedulingThread.class);
    private BaseIngestionRequestContext _requestContext;
    private IngestStrategyFactory _ingestStrategyFactory;
    private UnManagedVolumeService _unManagedVolumeService;
    private DbClient _dbClient;
    private Map<String, String> _taskMap;

    private static final String INGESTION_SUCCESSFUL_MSG = "Successfully ingested volume.";

    /**
     * Constructor. 
     * 
     * @param requestContext the BaseIngestionRequestContext
     * @param ingestStrategyFactory the IngestStrategyFactory
     * @param unManagedVolumeService the UnManagedVolumeService
     * @param dbClient the database client
     * @param taskMap a Map of UnManagedVolume ids to task ids
     */
    public IngestVolumesUnexportedSchedulingThread(BaseIngestionRequestContext requestContext,
            IngestStrategyFactory ingestStrategyFactory, UnManagedVolumeService unManagedVolumeService, DbClient dbClient,
            Map<String, String> taskMap) {
        this._requestContext = requestContext;
        this._ingestStrategyFactory = ingestStrategyFactory;
        this._unManagedVolumeService = unManagedVolumeService;
        this._dbClient = dbClient;
        this._taskMap = taskMap;
    }

    @Override
    public void run() {

        _requestContext.reset();
        while (_requestContext.hasNext()) {
            UnManagedVolume unManagedVolume = _requestContext.next();
            String taskId = _taskMap.get(unManagedVolume.getId().toString());

            try {
                _logger.info("Ingestion starting for unmanaged volume {}", unManagedVolume.getNativeGuid());
                List<URI> volList = new ArrayList<URI>();
                volList.add(_requestContext.getCurrentUnManagedVolumeUri());
                VolumeIngestionUtil.checkIngestionRequestValidForUnManagedVolumes(volList, _requestContext.getVpool(unManagedVolume),
                        _dbClient);

                IngestStrategy ingestStrategy = _ingestStrategyFactory.buildIngestStrategy(unManagedVolume,
                        !IngestStrategyFactory.DISREGARD_PROTECTION);

                @SuppressWarnings("unchecked")
                BlockObject blockObject = ingestStrategy.ingestBlockObjects(_requestContext,
                        VolumeIngestionUtil.getBlockObjectClass(unManagedVolume));

                if (null == blockObject) {
                    throw IngestionException.exceptions.generalVolumeException(
                            unManagedVolume.getLabel(), "check the logs for more details");
                }
                _logger.info("Ingestion completed successfully for unmanaged volume {}", unManagedVolume.getNativeGuid());

                _requestContext.getBlockObjectsToBeCreatedMap().put(blockObject.getNativeGuid(), blockObject);
                _requestContext.getProcessedUnManagedVolumeMap().put(
                        unManagedVolume.getNativeGuid(), _requestContext.getVolumeContext());

                String unManagedVolumeGUID = unManagedVolume.getNativeGuid();
                String taskMessage = "";
                boolean ingestedSuccessfully = false;
                if (unManagedVolume.getInactive()) {
                    ingestedSuccessfully = true;
                    taskMessage = INGESTION_SUCCESSFUL_MSG;
                } else {
                    // check in the created objects for corresponding block object without any internal flags set
                    BlockObject createdObject = _requestContext.findCreatedBlockObject(unManagedVolumeGUID.replace(
                            VolumeIngestionUtil.UNMANAGEDVOLUME,
                            VolumeIngestionUtil.VOLUME));
                    _logger.info("checking partial ingestion status of block object " + createdObject);
                    if ((null != createdObject)
                            && (!createdObject.checkInternalFlags(Flag.PARTIALLY_INGESTED) ||
                            // If this is an ingested RP volume in an uningested protection set, the ingest is successful.
                                    (createdObject instanceof Volume && ((Volume) createdObject).checkForRp() && ((Volume) createdObject)
                                            .getProtectionSet() == null))
                            ||
                    // If this is a successfully processed VPLEX backend volume, it will have the INTERNAL_OBJECT Flag
                            (VolumeIngestionUtil.isVplexBackendVolume(unManagedVolume) && createdObject
                                    .checkInternalFlags(Flag.INTERNAL_OBJECT))) {
                        _logger.info("successfully partially ingested block object {} ", createdObject.forDisplay());
                        ingestedSuccessfully = true;
                        taskMessage = INGESTION_SUCCESSFUL_MSG;
                    } else {
                        _logger.info("block object {} was not (partially) ingested successfully", createdObject);
                        ingestedSuccessfully = false;
                        StringBuffer taskStatus = _requestContext.getTaskStatusMap().get(unManagedVolume.getNativeGuid());
                        if (taskStatus == null) {
                            // No task status found. Put in a default message.
                            taskMessage = String.format("Not all the parent/replicas of unmanaged volume %s have been ingested",
                                    unManagedVolume.getLabel());
                        } else {
                            taskMessage = taskStatus.toString();
                        }
                    }
                }

                if (ingestedSuccessfully) {
                    _dbClient.ready(UnManagedVolume.class,
                            unManagedVolume.getId(), taskId, taskMessage);
                } else {
                    _dbClient.error(UnManagedVolume.class, unManagedVolume.getId(), taskId,
                            IngestionException.exceptions.unmanagedVolumeIsNotVisible(unManagedVolume.getLabel(), taskMessage));
                }

                // Commit any ingested CG
                _unManagedVolumeService.commitIngestedCG(_requestContext, unManagedVolume);

                // Commit the volume's internal resources
                _requestContext.getVolumeContext().commit();

                // Commit this volume's updated data objects if any after ingestion
                Set<DataObject> updatedObjects = _requestContext.getDataObjectsToBeUpdatedMap().get(unManagedVolumeGUID);
                if (updatedObjects != null && !updatedObjects.isEmpty()) {
                    for (DataObject dob : updatedObjects) {
                        _logger.info("Ingestion Wrap Up: Updating DataObject {} (hash {})", dob.forDisplay(), dob.hashCode());
                        _dbClient.updateObject(dob);
                    }
                }

                // Commit this volume's created data objects if any after ingestion
                Set<DataObject> createdObjects = _requestContext.getDataObjectsToBeCreatedMap().get(unManagedVolumeGUID);
                if (createdObjects != null && !createdObjects.isEmpty()) {
                    for (DataObject dob : createdObjects) {
                        _logger.info("Ingestion Wrap Up: Creating DataObject {} (hash {})", dob.forDisplay(), dob.hashCode());
                        _dbClient.createObject(dob);
                    }
                }

            } catch (APIException ex) {
                _logger.error("APIException occurred", ex);
                _dbClient.error(UnManagedVolume.class, _requestContext.getCurrentUnManagedVolumeUri(), taskId, ex);
                _requestContext.getVolumeContext().rollback();
            } catch (Exception ex) {
                _logger.error("Exception occurred", ex);
                _dbClient.error(UnManagedVolume.class, _requestContext.getCurrentUnManagedVolumeUri(),
                        taskId, IngestionException.exceptions.generalVolumeException(
                                unManagedVolume.getLabel(), ex.getLocalizedMessage()));
                _requestContext.getVolumeContext().rollback();
            }
        }

        for (BlockObject bo : _requestContext.getBlockObjectsToBeCreatedMap().values()) {
            _logger.info("Ingestion Wrap Up: Creating BlockObject {} (hash {})", bo.forDisplay(), bo.hashCode());
            _dbClient.createObject(bo);
        }
        for (UnManagedVolume umv : _requestContext.getUnManagedVolumesToBeDeleted()) {
            _logger.info("Ingestion Wrap Up: Deleting UnManagedVolume {} (hash {})", umv.forDisplay(), umv.hashCode());
            _dbClient.updateObject(umv);
        }

        // record the events after they have been persisted
        for (BlockObject volume : _requestContext.getBlockObjectsToBeCreatedMap().values()) {
            _unManagedVolumeService.recordVolumeOperation(_dbClient, _unManagedVolumeService.getOpByBlockObjectType(volume),
                    Status.ready, volume.getId());
        }
    }

    /**
     * Executes API Tasks on a separate thread by instantiating a IngestVolumesUnexportedSchedulingThread.
     * 
     * @param executorService the ExecutorService
     * @param requestContext the BaseIngestionRequestContext
     * @param ingestStrategyFactory the IngestStrategyFactory
     * @param unManagedVolumeService the UnManagedVolumeService
     * @param dbClient the database client
     * @param taskMap a Map of UnManagedVolume ids to task ids
     * @param taskList a list of Tasks
     */
    public static void executeApiTask(ExecutorService executorService, BaseIngestionRequestContext requestContext,
            IngestStrategyFactory ingestStrategyFactory, UnManagedVolumeService unManagedVolumeService, DbClient dbClient,
            Map<String, String> taskMap, TaskList taskList) {

        IngestVolumesUnexportedSchedulingThread schedulingThread = new IngestVolumesUnexportedSchedulingThread(requestContext,
                ingestStrategyFactory, unManagedVolumeService, dbClient, taskMap);

        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            String message = "Failed to start unmanaged volume ingestion tasks...";
            _logger.error(message, e);
            for (TaskResourceRep taskRep : taskList.getTaskList()) {
                taskRep.setMessage(message);
            }
        }
    }
}
