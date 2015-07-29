/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.Map;

import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * Processor to handle StorageSynchronized instances of local replica
 */
public class ReplicationRelationshipProcessor extends StorageProcessor {
    private final static Logger _logger = LoggerFactory
            .getLogger(ReplicationRelationshipProcessor.class);
    private final static String COPY_STATE = "CopyState";
    private final static String SYNC_TYPE = "SyncType";
    private final static String SYNC_STATE = "SyncState";
    private final static String EMC_RELATIONSHIP_NAME = "EMCRelationshipName";
    private final static String EMC_COPY_STATE_DESC = "EMCCopyStateDesc";
    private final static String INACTIVE = "INACTIVE";

    private static final String COPY_STATE_SYNCHRONIZED = "4";
    private static final String COPY_STATE_FRACTURED = "6";
    private static final String COPY_STATE_SPLIT = "7";
    private static final String COPY_STATE_INACTIVE = "8";
    // replica state for clone of snapshot, which is not restorable in ViPR
    private static final String SNAPSHOT_CLONE_REPLICA_STATE = Volume.ReplicationState.UNKNOWN.name();

    private Map<String, LocalReplicaObject> _volumeToLocalReplicaMap;
    private Map<String, String> _syncAspectMap;

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.debug("Calling ReplicationRelationshipProcessor");
        _volumeToLocalReplicaMap = (Map<String, LocalReplicaObject>) keyMap
                .get(Constants.UN_VOLUME_LOCAL_REPLICA_MAP);
        _syncAspectMap = (Map<String, String>) keyMap
                .get(Constants.SNAPSHOT_NAMES_SYNCHRONIZATION_ASPECT_MAP);

        CIMInstance[] instances = (CIMInstance[]) getFromOutputArgs((CIMArgument[]) resultObj, SmisConstants.SYNCHRONIZATIONS);
        if (instances == null) {
            _logger.info("No {} returned", SmisConstants.SYNCHRONIZATIONS);
            return;
        }

        _logger.info("Total StorageSynchronized instances {}", instances.length);
        processInstances(instances);
    }

    private void processInstances(CIMInstance[] instances) {
        for (CIMInstance instance : instances) {
            try {
                CIMObjectPath targetPath = (CIMObjectPath) instance.getPropertyValue(Constants._SyncedElement);
                CIMObjectPath sourcePath = (CIMObjectPath) instance.getPropertyValue(Constants._SystemElement);
                String nativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(targetPath);
                String srcNativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(sourcePath);
                _logger.info("Target Native Guid {}, Source Native Guid {}", nativeGuid, srcNativeGuid);
                String syncType = getCIMPropertyValue(instance, SYNC_TYPE);

                LocalReplicaObject replicaObj = _volumeToLocalReplicaMap
                        .get(nativeGuid);
                if (replicaObj == null) {
                    replicaObj = new LocalReplicaObject(nativeGuid);
                    _volumeToLocalReplicaMap.put(nativeGuid, replicaObj);
                } else {
                    // Target is already in the map, must also be a source
                    // as part of other StorageSynchronized instance(s).
                    // Need to set source
                    _logger.info("Found Target Local Replica Object {}",
                            replicaObj);
                    if (SYNC_TYPE_SNAPSHOT.equals(syncType)) {
                        StringSet fullCopies = replicaObj.getFullCopies();
                        if (fullCopies != null && !fullCopies.isEmpty()) {
                            for (String fullCopyNativeGuid : fullCopies) {
                                LocalReplicaObject fullCopy = _volumeToLocalReplicaMap.get(fullCopyNativeGuid);
                                fullCopy.setReplicaState(SNAPSHOT_CLONE_REPLICA_STATE);
                            }
                        }
                    }
                }

                // set source
                replicaObj.setSourceNativeGuid(srcNativeGuid);

                boolean isReplicaOfSnapshot = false;
                LocalReplicaObject srcReplicaObj = _volumeToLocalReplicaMap
                        .get(srcNativeGuid);
                if (srcReplicaObj == null) {
                    srcReplicaObj = new LocalReplicaObject(srcNativeGuid);
                    _volumeToLocalReplicaMap.put(srcNativeGuid,
                            srcReplicaObj);
                } else {
                    // A volume could be both a source or target in corner cases
                    // Source is already in the map, could also be a target
                    // as part of other StorageSynchronized instance(s).
                    // Need to set fullCopies/mirrors/snapshots accordingly
                    _logger.info("Found Source Local Replica Object {}",
                            srcReplicaObj);

                    if (LocalReplicaObject.Types.BlockSnapshot.equals(srcReplicaObj.getType())) {
                        isReplicaOfSnapshot = true;
                    }
                }

                String systemName = targetPath.getKey(Constants._SystemName).getValue().toString();
                String syncState = getCIMPropertyValue(instance, SYNC_STATE);
                String copyState = getCIMPropertyValue(instance, COPY_STATE);
                boolean inSync = COPY_STATE_SYNCHRONIZED.equals(copyState);
                if (SYNC_TYPE_CLONE.equals(syncType) ||
                        (SYNC_TYPE_MIRROR.equals(syncType) &&
                                // On VNX, full copies are actually mirrors
                                // Mirrors with restorable states (fractured and split, except synchronized) are treated as full copies,
                                // while mirrors with synchronized and other states are treated as mirrors
                                systemName.toLowerCase().startsWith(Constants.CLARIION) &&
                        (copyState.equals(COPY_STATE_FRACTURED) || copyState.equals(COPY_STATE_SPLIT)))) {
                    replicaObj.setType(LocalReplicaObject.Types.FullCopy);
                    replicaObj.setSyncActive(inSync);
                    replicaObj.setReplicaState(isReplicaOfSnapshot ? SNAPSHOT_CLONE_REPLICA_STATE : getReplicaState(copyState));

                    if (null == srcReplicaObj.getFullCopies()) {
                        srcReplicaObj.setFullCopies(new StringSet());
                    }

                    srcReplicaObj.getFullCopies().add(nativeGuid);
                } else if (SYNC_TYPE_MIRROR.equals(syncType)) {
                    replicaObj.setType(LocalReplicaObject.Types.BlockMirror);
                    replicaObj.setSyncType(syncType);
                    replicaObj.setSyncState(syncState);
                    replicaObj.setSynchronizedInstance(createStorageSynchronizedObjPath(targetPath, sourcePath));

                    if (null == srcReplicaObj.getMirrors()) {
                        srcReplicaObj.setMirrors(new StringSet());
                    }

                    srcReplicaObj.getMirrors().add(nativeGuid);
                } else if (SYNC_TYPE_SNAPSHOT.equals(syncType)) {
                    replicaObj.setType(LocalReplicaObject.Types.BlockSnapshot);
                    String emcCopyState = getCIMPropertyValue(instance, EMC_COPY_STATE_DESC);
                    if (INACTIVE.equals(emcCopyState)) {
                        // for an inactive snapshot, needsCopyToTarget has to be set,
                        // so that ViPR will try to "activate" it by calling copySnapshotToTarget during export
                        replicaObj.setNeedsCopyToTarget(true);
                    }

                    replicaObj.setSyncActive(inSync);
                    replicaObj
                            .setTechnologyType(BlockSnapshot.TechnologyType.NATIVE
                                    .name());

                    String relationshipName = getCIMPropertyValue(instance,
                            EMC_RELATIONSHIP_NAME);
                    String syncAspect = _syncAspectMap.get(getSyncAspectMapKey(
                            srcNativeGuid, relationshipName));
                    replicaObj.setSettingsInstance(syncAspect);

                    if (null == srcReplicaObj.getSnapshots()) {
                        srcReplicaObj.setSnapshots(new StringSet());
                    }

                    srcReplicaObj.getSnapshots().add(nativeGuid);
                } else {
                    _logger.debug("Ignore Target {}, SyncType {}", nativeGuid, syncType);
                }
            } catch (Exception e) {
                _logger.error("Exception on processing instances", e);
            }
        }
    }

    /**
     * create object path from source and target paths
     * 
     * @param targetPath
     * @param sourcePath
     * @return String
     */
    private String createStorageSynchronizedObjPath(CIMObjectPath targetPath, CIMObjectPath sourcePath) {
        @SuppressWarnings("rawtypes")
        CIMProperty[] propKeys = {
                new CIMProperty<String>(SmisConstants.CP_SYNCED_ELEMENT,
                        CIMDataType.STRING_T, targetPath.toString(), true,
                        false, null),
                new CIMProperty<String>(SmisConstants.CP_SYSTEM_ELEMENT,
                        CIMDataType.STRING_T, sourcePath.toString(), true,
                        false, null), };

        return CimObjectPathCreator.createInstance(
                Constants.STORAGE_SYNCHRONIZED_SV_SV, Constants.EMC_NAMESPACE,
                propKeys).toString();
    }

    /*
     * Translate CopyState to ViPR ReplicationState for clone
     * 
     * Clone can be restored if it is ReplicationState.SYNCHRONIZED
     * corresponding CopyState values are synchronized, fractured and split
     */
    private String getReplicaState(String copyState) {
        switch (copyState) {
            case COPY_STATE_SYNCHRONIZED:
            case COPY_STATE_FRACTURED:
            case COPY_STATE_SPLIT:
                return Volume.ReplicationState.SYNCHRONIZED.name();
            case COPY_STATE_INACTIVE:
                return Volume.ReplicationState.INACTIVE.name();
            default:
                return Volume.ReplicationState.UNKNOWN.name();
        }
    }
}
