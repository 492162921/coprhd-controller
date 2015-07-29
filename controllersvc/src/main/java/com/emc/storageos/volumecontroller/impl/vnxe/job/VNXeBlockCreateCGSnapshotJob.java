/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.ParametersOut;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.vnxe.models.VNXeLunGroupSnap;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;

public class VNXeBlockCreateCGSnapshotJob extends VNXeJob {

    private static final long serialVersionUID = -4468675691425659495L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeBlockCreateCGSnapshotJob.class);

    public VNXeBlockCreateCGSnapshotJob(String jobId,
            URI storageSystemUri, Boolean createInactive, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "createBlockCGSnapshot");

    }

    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }
            BlockSnapshotCreateCompleter completer = (BlockSnapshotCreateCompleter) getTaskCompleter();
            List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, completer.getSnapshotURIs());
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            if (_status == JobStatus.SUCCESS) {
                VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);
                VNXeCommandJob vnxeJob = vnxeApiClient.getJob(getJobIds().get(0));
                ParametersOut output = vnxeJob.getParametersOut();
                // get the luns belonging to the lun group
                String lunGroupSnapId = output.getId();
                VNXeLunGroupSnap groupSnap = vnxeApiClient.getLunGroupSnapshot(lunGroupSnapId);
                List<VNXeLun> groupLuns = vnxeApiClient.getLunByStorageResourceId(groupSnap.getStorageResource().getId());
                // Create mapping of volume.nativeDeviceId to BlockSnapshot object
                Map<String, BlockSnapshot> volumeToSnapMap = new HashMap<String, BlockSnapshot>();
                for (BlockSnapshot snapshot : snapshots) {
                    Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());
                    volumeToSnapMap.put(volume.getNativeId(), snapshot);
                }

                for (VNXeLun groupLun : groupLuns) {
                    BlockSnapshot snapshot = volumeToSnapMap.get(groupLun.getId());
                    if (snapshot == null) {
                        _logger.info("No snapshot found for the vnxe lun - ", groupLun.getId());
                        continue;
                    }
                    snapshot.setNativeId(output.getId());
                    snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, snapshot));
                    snapshot.setDeviceLabel(groupLun.getName());
                    snapshot.setReplicationGroupInstance(lunGroupSnapId);
                    snapshot.setIsSyncActive(true);
                    snapshot.setInactive(false);
                    snapshot.setCreationTime(Calendar.getInstance());
                    snapshot.setWWN(groupLun.getSnapWwn());
                    snapshot.setAllocatedCapacity(groupLun.getSnapsSizeAllocated());
                    snapshot.setProvisionedCapacity(groupLun.getSnapsSize());
                    _logger.info(String.format("Going to set blocksnapshot %1$s nativeId to %2$s (%3$s). Associated lun is %4$s (%5$s)",
                            snapshot.getId().toString(), output.getId(), snapshot.getLabel(), groupLun.getId(), groupLun.getName()));
                    dbClient.persistObject(snapshot);
                }

            } else if (_status == JobStatus.FAILED) {
                _logger.info("Failed to create snapshot");
                for (BlockSnapshot snapshot : snapshots) {
                    snapshot.setInactive(true);
                }
                dbClient.persistObject(snapshots);
            }
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeBlockCreateCGSnapshotJob", e);
            setErrorStatus("Encountered an internal error during group snapshot create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

}
