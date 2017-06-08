/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils.callEMCRefreshIfRequired;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CREATE_LIST_REPLICA;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.JOB;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.text.MessageFormat.format;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.cim.CIMArgument;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.SynchronizationState;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ReplicaOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisBlockDeleteListReplicaJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateListReplicaJob;

public abstract class AbstractReplicaOperations implements ReplicaOperations {
    private static final Logger _log = LoggerFactory.getLogger(AbstractReplicaOperations.class);

    protected DbClient _dbClient;
    protected SmisCommandHelper _helper;
    protected CIMObjectPathFactory _cimPath;
    protected NameGenerator _nameGenerator;

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setSmisCommandHelper(SmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    @Override
    public void createListReplica(StorageSystem storage, List<URI> replicaList, Boolean createInactive,
            TaskCompleter taskCompleter) {
        _log.info("createListReplica operation START");
        List<String> targetDeviceIds = new ArrayList<String>();

        try {
            List<String> sourceIds = new ArrayList<String>();
            List<String> labels = new ArrayList<String>();
            Map<String, URI> srcNativeIdToReplicaUriMap = new HashMap<String, URI>();
            Map<String, String> tgtToSrcMap = new HashMap<String, String>();
            String replicaGroupName = null;
            String sessionName = null;
            Volume source = null;
            boolean isThinlyProvisioned = false;
            for (URI replicaURI : replicaList) {
                BlockObject replica = BlockObject.fetch(_dbClient, replicaURI);
                source = (Volume) _helper.getSource(replica);
                // Use the existing replica group instance name for the new snaps to add.
                TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, source.getTenant().getURI());
                int maxLength = SmisConstants.MAX_VOLUME_NAME_LENGTH;
                if (storage.getUsingSmis80() && URIUtil.isType(replicaURI, BlockSnapshot.class)) {
                    maxLength = SmisConstants.MAX_SMI80_SNAPSHOT_NAME_LENGTH;
                }
                String label = _nameGenerator.generate(tenant.getLabel(), replica.getLabel(), replicaURI.toString(),
                        '-', maxLength);
                labels.add(label);
                replicaGroupName = replica.getReplicationGroupInstance();
                if (sessionName == null) {
                    sessionName = getSnapshotSessionNameFromReplicaGroupName(replicaGroupName, storage.getId());
                }
                String sourceNativeId = source.getNativeId();
                isThinlyProvisioned = source.getThinlyProvisioned();
                sourceIds.add(sourceNativeId);
                srcNativeIdToReplicaUriMap.put(sourceNativeId, replica.getId());

                if (storage.deviceIsType(Type.vnxblock)) {
                    // need to create target devices first
                    final URI poolId = source.getPool();
                    final List<String> newDeviceIds = ReplicationUtils.createTargetDevices(storage, replicaGroupName, label,
                            createInactive, 1, poolId, source.getCapacity(), isThinlyProvisioned, null, taskCompleter,
                            _dbClient, _helper, _cimPath);
                    targetDeviceIds.addAll(newDeviceIds);
                    tgtToSrcMap.put(newDeviceIds.get(0), source.getNativeId());
                }
            }

            int syncType = getSyncType(replicaList.get(0));
            CIMObjectPath[] sourceVolumePaths = _cimPath.getVolumePaths(storage, sourceIds.toArray(new String[sourceIds.size()]));
            CIMObjectPath[] targetDevicePaths = _cimPath.getVolumePaths(storage, targetDeviceIds.toArray(new String[targetDeviceIds.size()]));
            CIMObjectPath targetVPSnapPoolPath = null;
            if (syncType == SmisConstants.SNAPSHOT_VALUE && !volumeHasSnapshot(source)) {
                targetVPSnapPoolPath = ReplicationUtils.getTargetPoolForVPSnapCreation(storage, null, replicaGroupName,
                        isThinlyProvisioned, _dbClient, _helper, _cimPath);
            }
            CIMArgument[] inArgs = _helper.getCreateListReplicaInputArguments(storage, sourceVolumePaths, targetDevicePaths, labels, syncType,
                    replicaGroupName, sessionName, createInactive, targetVPSnapPoolPath);
            CIMArgument[] outArgs = new CIMArgument[5];
            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            _helper.invokeMethod(storage, replicationSvc, CREATE_LIST_REPLICA, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, JOB);
            ControllerServiceImpl.enqueueJob(
                    new QueueJob(new SmisCreateListReplicaJob(job,
                            storage.getId(), srcNativeIdToReplicaUriMap, tgtToSrcMap, syncType, !createInactive, taskCompleter)));
        } catch (Exception e) {
            final String errMsg = format(
                    "An exception occurred when trying to create list replica on storage system {0}", storage.getId());
            _log.error(errMsg, e);

            // Roll back changes
            ReplicationUtils.rollbackCreateReplica(storage, null, targetDeviceIds, taskCompleter, _dbClient, _helper, _cimPath);
            List<? extends BlockObject> replicas = BlockObject.fetch(_dbClient, replicaList);
            for (BlockObject replica : replicas) {
                replica.setInactive(true);
            }
            _dbClient.updateObject(replicas);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createListReplica", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _log.info("createListReplica operation END");
    }

    /**
     * Checks if the request is for second snapshot creation for the same volume.
     * This is a temporary fix for COP-20864 (OPT# 497150).
     * TODO remove this method and condition check once the OPT is resolved.
     *
     * @param source the volume
     * @return true, if is second snapshot request
     */
    private boolean volumeHasSnapshot(Volume source) {
        int snapshotCount = 0;
        if (source != null) {
            URIQueryResultList snapshotURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(
                    source.getId()), snapshotURIs);
            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotURIs);
            for (BlockSnapshot snapshot : snapshots) {
                if (snapshot != null && !snapshot.getInactive() && !isNullOrEmpty(snapshot.getNativeId())) {
                    // snapshot created on array
                    snapshotCount++;
                }
            }
        }
        return (snapshotCount > 0) ? true : false;
    }

    @Override
    public void detachListReplica(StorageSystem storage, List<URI> replicaList, TaskCompleter taskCompleter) {
        _log.info("detachListReplica operation START");

        try {
            List<? extends BlockObject> replicas = BlockObject.fetchAll(_dbClient, replicaList);
            modifyListReplica(storage, replicaList, replicas, SmisConstants.DETACH_VALUE, SmisConstants.NON_COPY_STATE);
            for (BlockObject replica : replicas) {
                if (replica instanceof BlockMirror) {
                    BlockMirror mirror = (BlockMirror) replica;
                    mirror.setReplicaState(ReplicationState.DETACHED.name());
                    mirror.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    mirror.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                    mirror.setSynchronizedInstance(NullColumnValueGetter.getNullStr());
                    mirror.setSyncState(NullColumnValueGetter.getNullStr());

                    Volume volume = _dbClient.queryObject(Volume.class, mirror.getSource());
                    if (volume.getMirrors() != null) {
                        volume.getMirrors().remove(mirror.getId().toString());
                        _dbClient.updateObject(volume);
                    }
                } else if (replica instanceof Volume) {
                    Volume clone = (Volume) replica;
                    ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(clone, _dbClient);
                    clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
                    clone.setReplicaState(ReplicationState.DETACHED.name());
                }
            }

            _dbClient.updateObject(replicas);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Problem making SMI-S call", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, error);
        }

        _log.info("detachListReplica operation END");
    }

    @Override
    public void fractureListReplica(StorageSystem storage, List<URI> replicaList, Boolean sync, TaskCompleter taskCompleter) {
        _log.info("fractureListReplica operation START");

        try {
            List<? extends BlockObject> replicas = BlockObject.fetch(_dbClient, replicaList);
            int operation = (sync != null && sync) ? SmisConstants.SPLIT_VALUE : SmisConstants.FRACTURE_VALUE;
            int copyState = (operation == SmisConstants.SPLIT_VALUE) ? SmisConstants.SPLIT : SmisConstants.FRACTURED;
            modifyListReplica(storage, replicaList, replicas, operation, copyState);

            for (BlockObject replica : replicas) {
                if (replica instanceof BlockMirror) {
                    ((BlockMirror) replica).setSyncState(SynchronizationState.FRACTURED.name());
                } else if (replica instanceof Volume) {
                    ((Volume) replica).setReplicaState(ReplicationState.SYNCHRONIZED.name());
                }
            }

            _dbClient.updateObject(replicas);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Problem making SMI-S call", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, error);
        }

        _log.info("fractureListReplica operation END");
    }

    @Override
    public void deleteListReplica(StorageSystem storage, List<URI> replicaList, TaskCompleter taskCompleter)
        throws DeviceControllerException {
        _log.info("deleteListReplica operation START");
        if (!((storage.getUsingSmis80() && storage.deviceIsType(Type.vmax)) || storage.deviceIsType(Type.vnxblock))) {
            throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        }

        try {
            String[] deviceIds = _helper.getBlockObjectNativeIds(replicaList);
            if (storage.checkIfVmax3()) {
                for (String deviceId : deviceIds) {
                    _helper.removeVolumeFromParkingSLOStorageGroup(storage, deviceId, false);
                    _log.info("Done invoking remove volume {} from parking SLO storage group", deviceId);
                }
            }

            CIMObjectPath[] devicePaths = _cimPath.getVolumePaths(storage, deviceIds);
            CIMObjectPath configSvcPath = _cimPath.getConfigSvcPath(storage);
            CIMArgument[] inArgs = null;
            if (storage.deviceIsType(Type.vnxblock)) {
                inArgs = _helper.getReturnElementsToStoragePoolArguments(devicePaths);
            } else {
                inArgs = _helper.getReturnElementsToStoragePoolArguments(devicePaths, SmisConstants.CONTINUE_ON_NONEXISTENT_ELEMENT);
            }

            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, configSvcPath, SmisConstants.RETURN_ELEMENTS_TO_STORAGE_POOL, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, SmisConstants.JOB);
            ControllerServiceImpl.enqueueJob(new QueueJob(new SmisBlockDeleteListReplicaJob(job,
                    storage.getId(), taskCompleter)));
        } catch (Exception e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError serviceError = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    /**
     * Invoke modifyListSynchronization for synchronized operations, e.g. fracture, detach, etc.
     *
     * @param storage
     * @param replicaList
     * @param operation
     * @param copyState
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    private void modifyListReplica(StorageSystem storage, List<URI> replicaList, List<? extends BlockObject> replicas, int operation,
            int copyState)
            throws Exception {

        callEMCRefreshIfRequired(_dbClient, _helper, storage, replicaList);
        List<CIMObjectPath> syncPaths = new ArrayList<CIMObjectPath>();
        for (BlockObject replica : replicas) {
            BlockObject source = _helper.getSource(replica);
            CIMObjectPath syncObject = _cimPath.getStorageSynchronized(storage, source, storage, replica);
            if (_helper.checkExists(storage, syncObject, false, false) == null) {
                _log.error("Storage synchronized instance is not available for replica {}", replica.getLabel());
                throw DeviceControllerException.exceptions.synchronizationInstanceNull(replica.getLabel());
            }

            syncPaths.add(syncObject);
        }

        CIMArgument[] inArgs = _helper.getModifyListReplicaInputArguments(syncPaths.toArray(new CIMObjectPath[] {}),
                operation, copyState);
        _helper.callModifyListReplica(storage, inArgs);
    }

    protected int getSyncType(URI uri) {
        int syncType;
        if (URIUtil.isType(uri, BlockMirror.class)) {
            syncType = SmisConstants.MIRROR_VALUE;
        } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            syncType = SmisConstants.SNAPSHOT_VALUE;
        } else {
            syncType = SmisConstants.CLONE_VALUE;
        }

        return syncType;
    }

    /**
     * Given a replication group name of a SnapVx linked target group, determine the session name
     * by finding any member of the group that is associated to the BlockSnapshotSession instance.
     *
     * @param replicaGroupName  Linked target replication group name.
     * @return                  SnapVx session name.
     */
    private String getSnapshotSessionNameFromReplicaGroupName(String replicaGroupName, URI storage) {
        List<BlockSnapshot> snapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(replicaGroupName, storage, _dbClient);

        for (BlockSnapshot snapshot : snapshots) {
            List<BlockSnapshotSession> sessions = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    BlockSnapshotSession.class,
                    ContainmentConstraint.Factory.getLinkedTargetSnapshotSessionConstraint(snapshot.getId()));

            if (sessions != null && !sessions.isEmpty()) {
                return sessions.get(0).getSessionLabel();
            }
        }

        return SmisConstants.DEFAULT_REPLICATION_SETTING_DATA_ELEMENT_NAME;
    }
}
