/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import static com.emc.storageos.vplexcontroller.VPlexControllerUtils.getDataObject;
import static com.emc.storageos.vplexcontroller.VPlexControllerUtils.getVPlexAPIClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockConsistencyGroupUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ClusterConsistencyGroupWrapper;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexConsistencyGroupInfo;
import com.emc.storageos.vplexcontroller.VPlexDeviceController.VPlexTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public abstract class AbstractConsistencyGroupManager implements ConsistencyGroupManager, Controller {
    protected static final String CREATE_CG_STEP = "createCG";
    protected static final String DELETE_CG_STEP = "deleteCG";
    protected static final String ADD_VOLUMES_TO_CG_STEP = "addVolumesToCG";
    protected static final String REMOVE_VOLUMES_FROM_CG_STEP = "removeVolumesFromCG";
    protected static final String DELETE_LOCAL_CG_STEP = "deleteLocalCG";
    
    protected static final String CREATE_CG_METHOD_NAME = "createCG";
    protected static final String DELETE_CG_METHOD_NAME = "deleteCG";
    protected static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";
    protected static final String UPDATE_CONSISTENCY_GROUP_METHOD_NAME = "updateConsistencyGroup";
    protected static final String DELETE_CONSISTENCY_GROUP_METHOD_NAME = "deleteConsistencyGroup";
    protected static final String CREATE_CONSISTENCY_GROUP_METHOD_NAME = "createConsistencyGroup";
    protected static final String RB_DELETE_CG_METHOD_NAME = "rollbackDeleteCG";

    // logger reference.
    private static final Logger log = LoggerFactory
        .getLogger(AbstractConsistencyGroupManager.class);
    
    protected DbClient dbClient;
    protected VPlexApiFactory vplexApiFactory;
    protected WorkflowService workflowService;
    
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }
    
    public VPlexApiFactory getVplexApiFactory() {
        return vplexApiFactory;
    }
    
    public void setVplexApiFactory(VPlexApiFactory vplexApiFactory) {
        this.vplexApiFactory = vplexApiFactory;
    }
    
    public void setWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }
    
    public WorkflowService getWorkflowService() {
        return workflowService;
    }
     
     /**
      * Based on a list of VPlex volumes and a ViPR consistency group, this method determines
      * the corresponding VPlex cluster names and VPlex consistency group names.
      * 
      * @param vplexVolumeURIs The VPlex virtual volumes to analyze 
      * @param cgName The ViPR BlockConsistencyGroup name
      * @return A mapping of VPlex cluster/consistency groups to their associated volumes
      */
     protected Map<ClusterConsistencyGroupWrapper, List<URI>> getClusterConsistencyGroupVolumes(List<URI> vplexVolumeURIs, String cgName) {
         Map<ClusterConsistencyGroupWrapper, List<URI>> clusterConsistencyGroups = 
                 new HashMap<ClusterConsistencyGroupWrapper, List<URI>>();

         for (URI vplexVolumeURI : vplexVolumeURIs) {
             Volume vplexVolume = getDataObject(Volume.class, vplexVolumeURI, dbClient);
             addVolumeToClusterConsistencyGroup(vplexVolume, clusterConsistencyGroups, cgName);
         }

         return clusterConsistencyGroups;
     }
     
     /**
      * Builds and adds a VPlex cluster/consistency group mapping for a given VPlex volume and ViPR
      * BlockConsistencyGroup name.
      * 
      * @param vplexVolume The VPlex virtual volume for which we want to find a VPlex cluster/
      *                    consistency group mapping.
      * @param clusterConsistencyGroupVolumes The Map to which we want to add the VPlex cluster/
      *                                       consistency group volume mapping.
      * @param cgName The ViPR BlockConsistencyGroup name.
      */
     protected void addVolumeToClusterConsistencyGroup(Volume vplexVolume, Map<ClusterConsistencyGroupWrapper, 
             List<URI>> clusterConsistencyGroupVolumes, String cgName) {
         ClusterConsistencyGroupWrapper clusterConsistencyGroup = 
                 getClusterConsistencyGroup(vplexVolume, cgName);

         if (!clusterConsistencyGroupVolumes.containsKey(clusterConsistencyGroup)) {
             clusterConsistencyGroupVolumes.put(clusterConsistencyGroup, new ArrayList<URI>());
         }

         clusterConsistencyGroupVolumes.get(clusterConsistencyGroup).add(vplexVolume.getId());
     }
     

     
    protected Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }
    
    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }
    
    @Override
    public void deleteConsistencyGroup(Workflow workflow, URI vplexURI, URI cgURI, String opId)
            throws ControllerException {       
        try {
            // Get the CG.
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);
            
            // Add step to delete local. There should only be a local for
            // VPLEX CGs and there should be either one or two depending upon
            // if the volumes in the CG are local or distributed.
            String waitFor = null;
            if (cg.checkForType(Types.LOCAL)) {
                List<URI> localSystemUris = BlockConsistencyGroupUtils
                    .getLocalSystems(cg, dbClient);
                boolean localCGsDeleted = false;
                for (URI localSystemUri : localSystemUris) {
                    StorageSystem localSystem = getDataObject(StorageSystem.class,
                        localSystemUri, dbClient);
                    String localCgName = cg.fetchArrayCgName(localSystemUri);
                    Workflow.Method deleteCGMethod = new Workflow.Method(
                        DELETE_CONSISTENCY_GROUP_METHOD_NAME, localSystemUri, cgURI, Boolean.FALSE);
                    Workflow.Method rollbackDeleteCGMethod = new Workflow.Method(
                        CREATE_CONSISTENCY_GROUP_METHOD_NAME, localSystemUri, cgURI);
                    workflow.createStep(DELETE_LOCAL_CG_STEP, String.format(
                        "Deleting Consistency Group %s on local system %s", localCgName,
                        localSystemUri.toString()), null, localSystemUri, localSystem.getSystemType(),
                        BlockDeviceController.class, deleteCGMethod, rollbackDeleteCGMethod, null);
                    localCGsDeleted = true;
                }
                if (localCGsDeleted) {
                    waitFor = DELETE_LOCAL_CG_STEP;
                }
            }            
            
            // We need to examine the association of VPlex systems to VPlex CGs that 
            // have been created. We can't depend on the Volume's in the CG to determine 
            // the VPlex systems and CG names because there may not be any volumes in the CG
            // at this point.
            if (BlockConsistencyGroupUtils.referencesVPlexCGs(cg, dbClient)) {
                for (StorageSystem storageSystem : BlockConsistencyGroupUtils.getVPlexStorageSystems(cg, dbClient)) {
                    URI vplexSystemUri = storageSystem.getId();
                    
                    // Iterate over the VPlex consistency groups that need to be
                    // deleted.
                    for (String clusterCgName : cg.getSystemConsistencyGroups().get(vplexSystemUri.toString())) {
                        String clusterName = BlockConsistencyGroupUtils.fetchClusterName(clusterCgName);
                        String cgName = BlockConsistencyGroupUtils.fetchCgName(clusterCgName);
                        String stepId = workflow.createStepId();
                        Workflow.Method deletCGRollbackMethod = rollbackDeleteCGMethod(cgURI, stepId);
                        // Create the steps in the workflow to delete the consistency
                        // group. Note that we assume the consistency group does not
                        // contain any volumes. Currently, the API service does not allow
                        // this, and so this should never be called otherwise.
                        addStepForRemoveVPlexCG(workflow, stepId, waitFor, storageSystem,
                            cgURI, cgName, clusterName, Boolean.TRUE, deletCGRollbackMethod);
                    }
                }
            } 
            
            TaskCompleter completer = new VPlexTaskCompleter(BlockConsistencyGroup.class,
                Arrays.asList(cgURI), opId, null);
            log.info("Executing workflow plan");
            workflow.executePlan(completer, String.format(
                "Deletion of consistency group %s completed successfully", cgURI));
            log.info("Workflow plan executed");
        } catch (Exception e) {
            String failMsg = String.format("Deletion of consistency group %s failed",
                cgURI);
            log.error(failMsg, e);
            TaskCompleter completer = new VPlexTaskCompleter(BlockConsistencyGroup.class,
                Arrays.asList(cgURI), opId, null);
            String opName = ResourceOperationTypeEnum.DELETE_CONSISTENCY_GROUP.getName();
            ServiceError serviceError = VPlexApiException.errors.deleteConsistencyGroupFailed(
                    cgURI.toString(), opName, e);
            completer.error(dbClient, serviceError);
        }
    }
    
    /**
     * Deletes the consistency group with the passed URI on the VPLEX storage
     * system with the passed URU.
     * 
     * @param vplexSystemURI The URI of the VPlex system.
     * @param cgUri The URI of the ViPR consistency group.
     * @param cgName The name of the VPlex consistency group to delete.
     * @param clusterName The name of the VPlex cluster.
     * @param setInative true to mark the CG for deletion.
     * @param stepId The workflow step identifier.
     * 
     * @throws WorkflowException When an error occurs updating the work step
     *         state.
     */
    public void deleteCG(URI vplexSystemURI, URI cgUri, String cgName,
        String clusterName, Boolean setInactive, String stepId) throws WorkflowException {
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);
            log.info("Updated workflow step to executing");

            StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexSystemURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem, dbClient);
            log.info("Got VPlex API client for VPlex system {}", vplexSystemURI);
                        
            // Make a call to the VPlex API client to delete the consistency group.
            client.deleteConsistencyGroup(cgName);
            log.info("Deleted consistency group");
            
            // Create the rollback data in case this needs to be recreated.
            VPlexDeleteCGRollbackData rbData = new VPlexDeleteCGRollbackData();
            rbData.setVplexSystemURI(vplexSystemURI);
            rbData.setCgName(cgName);
            rbData.setClusterName(clusterName);
            rbData.setIsDistributed(new Boolean(getIsCGDistributed(client, cgName, clusterName)));
            workflowService.storeStepData(stepId, rbData);

            // Update the consistency group in the database.
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgUri, dbClient);
            cg.removeSystemConsistencyGroup(vplexSystemURI.toString(), 
                    BlockConsistencyGroupUtils.buildClusterCgName(clusterName, cgName));
            dbClient.persistObject(cg);
            
            // Only mark the ViPR CG for deletion when all associated VPlex CGs
            // have been deleted.
            if ((setInactive) && (!BlockConsistencyGroupUtils.referencesVPlexCGs(cg, dbClient))) {
                dbClient.markForDeletion(cg);
                log.info("Marked consistency group for deletion");
            }
            
            // Update step status to success.
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            log.error("Exception deleting consistency group: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            log.error("Exception deleting consistency group: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_CONSISTENCY_GROUP.getName();
            ServiceError serviceError = VPlexApiException.errors.deleteCGFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }
    
    @Override
    public void deleteConsistencyGroupVolume(URI vplexURI, Volume volume, String cgName) throws URISyntaxException {
        VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexURI, dbClient);
        
        // Determine the VPlex CG corresponding to the this volume
        ClusterConsistencyGroupWrapper clusterCg = 
                getClusterConsistencyGroup(volume, cgName);

        if (clusterCg != null) {
            // Remove the volume from the CG. Delete the CG if it's empty 
            // and the deleteCGWhenEmpty flag is set.   
            client.removeVolumesFromConsistencyGroup(
                    Arrays.asList(volume.getDeviceLabel()), clusterCg.getCgName(),
                    false);
            
            // De-reference the CG
            volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
            dbClient.persistObject(volume); 
        }
    }    

    /**
     * Gets the VPlex consistency group name that corresponds to the volume
     * and BlockConsistencyGroup.
     * 
     * @param volume The virtual volume used to determine cluster configuration.
     * @param cgURI The BlockConsistencyGroup id.
     * @return The VPlex consistency group name
     */
    protected String getVplexCgName(Volume volume, URI cgURI) {
        BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, dbClient);
        
        ClusterConsistencyGroupWrapper clusterConsistencyGroup = 
                getClusterConsistencyGroup(volume, cg.getLabel());
        
        return clusterConsistencyGroup.getCgName();
    }  
   
    @Override
    public void addVolumeToCg(URI cgURI, Volume vplexVolume, VPlexApiClient client, boolean addToViPRCg) {
        BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class,
                cgURI, dbClient);
        String cgName = cg.getLabel();

        ClusterConsistencyGroupWrapper clusterCgWrapper =
                this.getClusterConsistencyGroup(vplexVolume, cgName);
        
        log.info("Adding volumes to consistency group: " + clusterCgWrapper.getCgName());
        // Add the volume from the CG.
        client.addVolumesToConsistencyGroup(clusterCgWrapper.getCgName(), Arrays.asList(vplexVolume.getDeviceLabel()));
        
        if (addToViPRCg) {
            vplexVolume.setConsistencyGroup(cgURI);
            dbClient.updateAndReindexObject(vplexVolume);
        }
    }
 
    @Override
    public void removeVolumeFromCg(URI cgURI, Volume vplexVolume, VPlexApiClient client, boolean removeFromViPRCg) {
        BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class,
                cgURI, dbClient);
        String cgName = cg.getLabel();

        ClusterConsistencyGroupWrapper clusterCgWrapper =
                this.getClusterConsistencyGroup(vplexVolume, cgName);
        
        log.info("Removing volumes from consistency group: " + clusterCgWrapper.getCgName());
        // Remove the volumes from the CG.
        client.removeVolumesFromConsistencyGroup(
                Arrays.asList(vplexVolume.getDeviceLabel()), clusterCgWrapper.getCgName(), false);
        
        if (removeFromViPRCg) {
            vplexVolume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
            dbClient.updateAndReindexObject(vplexVolume);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void updateConsistencyGroup(Workflow workflow, URI vplexURI, URI cgURI,
        List<URI> addVolumesList, List<URI> removeVolumesList, String opId)
        throws InternalException {
        BlockConsistencyGroupUpdateCompleter completer = new BlockConsistencyGroupUpdateCompleter(cgURI, opId);
        ServiceError error = VPlexApiException.errors.unsupportedConsistencyGroupOpError(
            UPDATE_CONSISTENCY_GROUP_METHOD_NAME, cgURI.toString());
        completer.error(dbClient, error);
    }
    
    /**
     * Determine if the consistency group with the passed name on the passed cluster
     * is distributed.
     * 
     * @param client A reference to a VPLEX client
     * @param cgName The consistency group name
     * @param cgCluster The consistency group cluster
     * 
     * @return true if the consistency group is distributed, false otherwise.
     */
    private boolean getIsCGDistributed(VPlexApiClient client, String cgName, String cgCluster) {
        log.info("Determine if CG {} on cluster {} is distributed", cgName, cgCluster);
        boolean isDistributed = false;
        List<VPlexConsistencyGroupInfo> cgInfos = client.getConsistencyGroups();
        for (VPlexConsistencyGroupInfo cgInfo : cgInfos) {
            if ((cgInfo.getClusterName().equals(cgCluster)) && (cgInfo.getName().equals(cgName))) {
                log.info("CG is distributed");
                isDistributed = cgInfo.isDistributed();
            }
        }
        
        return isDistributed;
    }
    
    /**
     * Create the workflow method to rollback a CG deletion on a VPLEX system.
     * 
     * @param cgURI The consistency group URI
     * @param deleteStepId The step that deleted the CG.
     * 
     * @return A reference to the workflow method
     */
    private Workflow.Method rollbackDeleteCGMethod(URI cgURI, String deleteStepId) {
        return new Workflow.Method(RB_DELETE_CG_METHOD_NAME, cgURI, deleteStepId);
    }
    
    /**
     * Method call when we need to rollback the deletion of a consistency group.
     * 
     * @param cgURI The consistency group URI
     * @param deleteStepId The step that deleted the CG.
     * @param stepId The step id.
     */
    public void rollbackDeleteCG(URI cgURI, String deleteStepId, String stepId) {
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);
            log.info("Updated workflow step to executing");

            // Get the rollback data.
            Object rbDataObj = workflowService.loadStepData(deleteStepId);
            if (rbDataObj == null) {
                // Update step state to done.
                log.info("CG was not deleted, nothing to do.");
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }
            VPlexDeleteCGRollbackData rbData = (VPlexDeleteCGRollbackData) rbDataObj;

            // Get the VPlex API client.
            URI vplexSystemURI = rbData.getVplexSystemURI();
            StorageSystem vplexSystem = getDataObject(StorageSystem.class,
                vplexSystemURI, dbClient);
            VPlexApiClient client = getVPlexAPIClient(vplexApiFactory, vplexSystem,
                dbClient);
            log.info("Got VPlex API client for VPlex system {}", vplexSystemURI);

            // Recreate the consistency group on the VPLEX.
            String cgName = rbData.getCgName();
            String clusterName = rbData.getClusterName();
            client.createConsistencyGroup(cgName, clusterName, rbData.getIsDistributed()
                .booleanValue());
            log.info("Recreated CG {} on system {}", cgName, vplexSystemURI);

            // Update the consistency group in the database.
            BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI,
                dbClient);
            cg.addSystemConsistencyGroup(vplexSystemURI.toString(),
                BlockConsistencyGroupUtils.buildClusterCgName(clusterName, cgName));
            dbClient.persistObject(cg);
            log.info("Updated consistency group in database");
            
            // Update step state to done.
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (VPlexApiException vae) {
            log.error("Exception rolling back VPLEX consistency group deletion: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            log.error("Exception rolling back VPLEX consistency group deletion: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_CONSISTENCY_GROUP.getName();
            ServiceError serviceError = VPlexApiException.errors.rollbackDeleteCGFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    /**
     * 
     * @param workflow
     * @param stepId
     * @param waitFor
     * @param vplexSystem
     * @param cgURI
     * @param cgName
     * @param clusterName
     * @param setInactive
     * @param rollbackMethod
     */
    public void addStepForRemoveVPlexCG(Workflow workflow, String stepId,
        String waitFor, StorageSystem vplexSystem, URI cgURI, String cgName,
        String clusterName, Boolean setInactive, Workflow.Method rollbackMethod) {
        URI vplexSystemURI = vplexSystem.getId();
        Workflow.Method vplexExecuteMethod = new Workflow.Method(
            DELETE_CG_METHOD_NAME, vplexSystemURI, cgURI, cgName, clusterName, setInactive);
        workflow.createStep(DELETE_CG_STEP, String.format(
            "Deleting Consistency Group %s on VPLEX system %s", cgName, vplexSystemURI.toString()),
            waitFor, vplexSystemURI, vplexSystem.getSystemType(), getClass(),
            vplexExecuteMethod, rollbackMethod, stepId);
        log.info("Created step for delete CG {} on VPLEX {}", clusterName + ":" + cgName, vplexSystemURI);
    }
}
