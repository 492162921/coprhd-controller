/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.RPBlockServiceApiImpl;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Block snapshot session implementation for RP protected volumes.
 */
public class RPBlockSnapshotSessionApiImpl extends DefaultBlockSnapshotSessionApiImpl {
    /**
     * Private default constructor should not be called outside class.
     */
    @SuppressWarnings("unused")
    private RPBlockSnapshotSessionApiImpl() {
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
    public RPBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator, PermissionsHelper permissionsHelper,
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
        // If the requested source is a simple volume that is RP protected,
        // then we simply do platform specific validation for that volume's
        // platform.
        URI requestedSourceURI = requestedSourceObj.getId();
        if (URIUtil.isType(requestedSourceURI, Volume.class)) {
            Volume sourceVolume = (Volume) requestedSourceObj;
            boolean protectionBased = RPBlockServiceApiImpl.isProtectionBasedSnapshot(sourceVolume,
                    BlockSnapshot.TechnologyType.NATIVE.name());
            if (protectionBased) {
                throw APIException.badRequests.createSnapSessionNotSupportedForRPProtected();
            }

            // Validate using the appropriate implementation.
            BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(requestedSourceObj.getStorageController());
            snapSessionImpl.validateSnapshotSessionCreateRequest(requestedSourceObj, sourceObjList, project, name, newTargetsCount,
                    newTargetsName, newTargetCopyMode, skipInternalCheck, fcManager);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(BlockObject sourceObj, List<URI> snapSessionURIs,
            Map<URI, List<URI>> snapSessionSnapshotMap, String copyMode, String taskId) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(sourceObj.getStorageController());
        snapSessionImpl.createSnapshotSession(sourceObj, snapSessionURIs, snapSessionSnapshotMap, copyMode, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateLinkNewTargetsRequest(BlockObject snapSessionSourceObj, Project project, int newTargetsCount,
            String newTargetsName, String newTargetCopyMode) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.validateLinkNewTargetsRequest(snapSessionSourceObj, project, newTargetsCount, newTargetsName, newTargetCopyMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkNewTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            List<URI> snapshotURIs, String copyMode, String taskId) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.linkNewTargetVolumesToSnapshotSession(snapSessionSourceObj, snapSession, snapshotURIs, copyMode, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRelinkSnapshotSessionTargets(BlockObject snapSessionSourceObj, BlockSnapshotSession tgtSnapSession,
            Project project, List<URI> snapshotURIs, UriInfo uriInfo) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.validateRelinkSnapshotSessionTargets(snapSessionSourceObj, tgtSnapSession, project, snapshotURIs, uriInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession TgtSnapSession,
            List<URI> snapshotURIs, String taskId) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.relinkTargetVolumesToSnapshotSession(snapSessionSourceObj, TgtSnapSession, snapshotURIs, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateUnlinkSnapshotSessionTargets(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project,
            Set<URI> snapshotURIs, UriInfo uriInfo) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.validateUnlinkSnapshotSessionTargets(snapSession, snapSessionSourceObj, project, snapshotURIs, uriInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlinkTargetVolumesFromSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            Map<URI, Boolean> snapshotDeletionMap, String taskId) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.unlinkTargetVolumesFromSnapshotSession(snapSessionSourceObj, snapSession, snapshotDeletionMap, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateRestoreSnapshotSession(BlockObject snapSessionSourceObj, Project project) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.validateRestoreSnapshotSession(snapSessionSourceObj, project);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.restoreSnapshotSession(snapSession, snapSessionSourceObj, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateDeleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.validateDeleteSnapshotSession(snapSession, snapSessionSourceObj, project);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(snapSessionSourceObj.getStorageController());
        snapSessionImpl.deleteSnapshotSession(snapSession, snapSessionSourceObj, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockSnapshotSession> getSnapshotSessionsForSource(BlockObject sourceObj) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(sourceObj.getStorageController());
        return snapSessionImpl.getSnapshotSessionsForSource(sourceObj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockSnapshotSession> prepareSnapshotSessions(List<BlockObject> sourceObjList, String snapSessionLabel, int newTargetCount,
            String newTargetsName, List<URI> snapSessionURIs, Map<URI, Map<URI, BlockSnapshot>> snapSessionSnapshotMap, String taskId) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(sourceObjList.get(0).getStorageController());
        return snapSessionImpl.prepareSnapshotSessions(sourceObjList, snapSessionLabel, newTargetCount, newTargetsName, snapSessionURIs,
                snapSessionSnapshotMap, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<URI, BlockSnapshot> prepareSnapshotsForSession(BlockObject sourceObj, int sessionCount, int newTargetCount,
            String newTargetsName) {
        // Important: that the only difference between these snapshots and snapshots created with the
        // create snapshot APIs is that the parent and project NamedURIs for those snapshots use the
        // snapshot label rather than the source label. This is an inconsistency between non-RP snaps
        // and other snaps and should probably be fixed.
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(sourceObj.getStorageController());
        Map<URI, BlockSnapshot> snapshotMap = snapSessionImpl.prepareSnapshotsForSession(sourceObj, sessionCount, newTargetCount,
                newTargetsName);
        for (BlockSnapshot snapshot : snapshotMap.values()) {
            // This is a native snapshot so do not set the consistency group, otherwise
            // the SMIS code/array will get confused trying to look for a consistency
            // group that only exists in RecoverPoint.
            snapshot.setConsistencyGroup(null);
            _dbClient.updateObject(snapshot);
        }
        return snapshotMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyActiveMirrors(Volume sourceVolume) {
        BlockSnapshotSessionApi snapSessionImpl = getImplementationForBackendSystem(sourceVolume.getStorageController());
        snapSessionImpl.verifyActiveMirrors(sourceVolume);
    }
}
