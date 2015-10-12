/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.vplexcontroller.VPlexController;

/**
 * Block snapshot session implementation for volumes on VPLEX systems.
 */
public class VPlexBlockSnapshotSessionApiImpl extends DefaultBlockSnapshotSessionApiImpl {

    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private VPlexBlockSnapshotSessionApiImpl() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param dbClient A reference to a data base client.
     * @param coordinator A reference to the coordinator client.
     * @param permissionsHelper A reference to a permission helper.
     * @param securityContext A reference to the security context.
     * @param blockSnapshotSessionMgr A reference to the snapshot session manager.
     */
    public VPlexBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator, PermissionsHelper permissionsHelper,
            SecurityContext securityContext, BlockSnapshotSessionManager blockSnapshotSessionMgr) {
        super(dbClient, coordinator, permissionsHelper, securityContext, blockSnapshotSessionMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, int newTargetsCount, String newTargetsName, String newTargetCopyMode, boolean skipInternalCheck,
            BlockFullCopyManager fcManager) {
        // We can only create a snapshot session for a VPLEX volume, where the
        // source side backend volume supports the creation of a snapshot session.
        for (BlockObject sourceObj : sourceObjList) {
            URI sourceURI = sourceObj.getId();
            if (URIUtil.isType(sourceURI, Volume.class)) {
                // TBD - Add a check in case this is called with a backend volume.

                // Get the platform specific implementation for the source side
                // backend storage system and call the validation routine.
                Volume vplexVolume = (Volume) sourceObj;
                BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
                StorageSystem srcSideBackendSystem = _dbClient.queryObject(StorageSystem.class,
                        srcSideBackendVolume.getStorageController());
                BlockSnapshotSessionApi snapSessionImpl = _blockSnapshotSessionMgr
                        .getPlatformSpecificImplForSystem(srcSideBackendSystem);
                snapSessionImpl.validateSnapshotSessionCreateRequest(srcSideBackendVolume, Arrays.asList(srcSideBackendVolume),
                        project, name, newTargetsCount, newTargetsName, newTargetCopyMode, true, fcManager);
            } else {
                // We don't currently support snaps of BlockSnapshot instances
                // so should never be called.
                throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(BlockObject sourceObj, List<URI> snapSessionURIs,
            Map<URI, List<URI>> snapSessionSnapshotMap, String copyMode, String taskId) {
        if (URIUtil.isType(sourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the create method.
            Volume vplexVolume = (Volume) sourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            StorageSystem srcSideBackendSystem = _dbClient.queryObject(StorageSystem.class,
                    srcSideBackendVolume.getStorageController());
            BlockSnapshotSessionApi snapSessionImpl = _blockSnapshotSessionMgr
                    .getPlatformSpecificImplForSystem(srcSideBackendSystem);
            snapSessionImpl.createSnapshotSession(srcSideBackendVolume, snapSessionURIs, snapSessionSnapshotMap, copyMode, taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateLinkNewTargetsRequest(BlockObject snapSessionSourceObj, Project project, int newTargetsCount,
            String newTargetsName, String newTargetCopyMode) {
        URI sourceURI = snapSessionSourceObj.getId();
        if (URIUtil.isType(sourceURI, Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.validateLinkNewTargetsRequest(srcSideBackendVolume, project, newTargetsCount, newTargetsName,
                    newTargetCopyMode);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkNewTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            List<URI> snapshotURIs, String copyMode, String taskId) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the link method.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.linkNewTargetVolumesToSnapshotSession(srcSideBackendVolume, snapSession, snapshotURIs, copyMode, taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRelinkSnapshotSessionTargets(BlockObject snapSessionSourceObj, BlockSnapshotSession tgtSnapSession,
            Project project, List<URI> snapshotURIs, UriInfo uriInfo) {
        URI sourceURI = snapSessionSourceObj.getId();
        if (URIUtil.isType(sourceURI, Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.validateRelinkSnapshotSessionTargets(srcSideBackendVolume, tgtSnapSession, project, snapshotURIs, uriInfo);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession TgtSnapSession,
            List<URI> snapshotURIs, String taskId) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the relink method.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.relinkTargetVolumesToSnapshotSession(srcSideBackendVolume, TgtSnapSession, snapshotURIs, taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateUnlinkSnapshotSessionTargets(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project,
            Set<URI> snapshotURIs, UriInfo uriInfo) {
        URI sourceURI = snapSessionSourceObj.getId();
        if (URIUtil.isType(sourceURI, Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.validateUnlinkSnapshotSessionTargets(snapSession, srcSideBackendVolume, project, snapshotURIs, uriInfo);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlinkTargetVolumesFromSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            Map<URI, Boolean> snapshotDeletionMap, String taskId) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the unlink target method.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.unlinkTargetVolumesFromSnapshotSession(srcSideBackendVolume, snapSession, snapshotDeletionMap, taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRestoreSnapshotSession(BlockObject snapSessionSourceObj, Project project) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.validateRestoreSnapshotSession(srcSideBackendVolume, project);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId) {
        // Because the source is a VPLEX volume, when the native array snapshot is restored, the
        // data on the source side backend volume will be restored to the data on the backend array
        // snapshot. This means we have to perform operations on the VPLEX volume to ensure it
        // recognizes that the data has been changed.
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            URI vplexURI = vplexVolume.getStorageController();
            VPlexController controller = getController(VPlexController.class,
                    DiscoveredDataObject.Type.vplex.toString());
            controller.restoreSnapshotSession(vplexURI, snapSession.getId(), taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateDeleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the validation routine.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.validateDeleteSnapshotSession(snapSession, srcSideBackendVolume, project);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId) {
        if (URIUtil.isType(snapSessionSourceObj.getId(), Volume.class)) {
            // Get the platform specific implementation for the source side
            // backend storage system and call the delete method.
            Volume vplexVolume = (Volume) snapSessionSourceObj;
            BlockObject srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(srcSideBackendVolume.getStorageController());
            snapSessionImpl.deleteSnapshotSession(snapSession, srcSideBackendVolume, taskId);
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should never be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockSnapshotSession> getSnapshotSessionsForSource(BlockObject sourceObj) {
        List<BlockSnapshotSession> snapSessions;
        if (URIUtil.isType(sourceObj.getId(), Volume.class)) {
            Volume vplexVolume = (Volume) sourceObj;
            Volume srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume(vplexVolume, true, _dbClient);
            URI parentURI = srcSideBackendVolume.getId();
            snapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, BlockSnapshotSession.class,
                    ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(parentURI));
        } else {
            // We don't currently support snaps of BlockSnapshot instances
            // so should not be called.
            throw APIException.methodNotAllowed.notSupportedForVplexVolumes();
        }

        return snapSessions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<URI, BlockSnapshot> prepareSnapshotsForSession(BlockObject sourceObj, int sourceCount, int newTargetCount,
            String newTargetsName) {
        // The snapshots are generally prepared with information from the
        // source side backend volume, which is the volume being snapped.
        // The passed source object will be a volume, else would not have
        // made it this far.
        Volume srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume((Volume) sourceObj, true, _dbClient);
        Map<URI, BlockSnapshot> snapshotMap = super.prepareSnapshotsForSession(srcSideBackendVolume, sourceCount,
                newTargetCount, newTargetsName);

        // However, the project is from the VPLEX volume.
        for (BlockSnapshot snapshot : snapshotMap.values()) {
            Project sourceProject = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObj, _dbClient);
            snapshot.setProject(new NamedURI(sourceProject.getId(), sourceObj.getLabel()));
            _dbClient.persistObject(snapshot);
        }

        return snapshotMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BlockSnapshotSession prepareSnapshotSessionFromSource(BlockObject sourceObj, String snapSessionLabel, String instanceLabel,
            String taskId) {
        // The session is generally prepared with information from the
        // source side backend volume, which is the volume being snapped.
        // The passed source object will be a volume, else would not have
        // made it this far.
        Volume srcSideBackendVolume = VPlexUtil.getVPLEXBackendVolume((Volume) sourceObj, true, _dbClient);
        BlockSnapshotSession snapSession = super.prepareSnapshotSessionFromSource(srcSideBackendVolume, snapSessionLabel, instanceLabel,
                taskId);

        // However, the project is from the VPLEX volume.
        Project sourceProject = BlockSnapshotSessionUtils.querySnapshotSessionSourceProject(sourceObj, _dbClient);
        snapSession.setProject(new NamedURI(sourceProject.getId(), sourceObj.getLabel()));

        return snapSession;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void checkPendingTasksOnSourceVolume(Volume sourceVolume) {
        // We'll check the passed backend volume for tasks.
        super.checkPendingTasksOnSourceVolume(sourceVolume);

        // Check for pending tasks on the VPLEX volume too.
        Volume vplexVolume = Volume.fetchVplexVolume(_dbClient, sourceVolume);
        BlockServiceUtils.checkForPendingTasks(Arrays.asList(vplexVolume.getTenant().getURI()),
                Arrays.asList(vplexVolume), _dbClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void verifyActiveMirrors(Volume sourceVolume) {
        // We'll check the passed backend volume active mirrors.
        super.checkPendingTasksOnSourceVolume(sourceVolume);

        // Check the VPLEX volume too.
        Volume vplexVolume = Volume.fetchVplexVolume(_dbClient, sourceVolume);
        List<URI> activeMirrorsForSource = BlockServiceUtils.getActiveMirrorsForVplexVolume(vplexVolume, _dbClient);
        if (!activeMirrorsForSource.isEmpty()) {
            throw APIException.badRequests.snapshotSessionSourceHasActiveMirrors(
                    vplexVolume.getLabel(), activeMirrorsForSource.size());
        }
    }

    /**
     * Get the BlockSnapshotSessionApi implementation for the system with the passed URI.
     * 
     * @param backendSystemURI The URI of a backend storage system for a VPLEX volume.
     * 
     * @return The BlockSnapshotSessionApi implementation for the backend storage system.
     */
    private BlockSnapshotSessionApi getImplementationForBackendSystem(URI backendSystemURI) {
        StorageSystem srcSideBackendSystem = _dbClient.queryObject(StorageSystem.class, backendSystemURI);
        BlockSnapshotSessionApi snapSessionImpl = _blockSnapshotSessionMgr
                .getPlatformSpecificImplForSystem(srcSideBackendSystem);
        return snapSessionImpl;
    }
}
