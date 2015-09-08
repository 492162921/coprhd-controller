/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when SMI-S request to unlink a target from
 * an array snapshot completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionUnlinkTargetCompleter extends TaskLockingCompleter {

    // The URI of the BlockSnapshotSession representing the array snapshot.
    private final URI _snapSessionURI;

    // Whether or not the target is deleted when unlinked.
    private final Boolean _deleteTarget;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionUnlinkTargetCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param snapshotURI The id of the BlockSnapshot instance representing the target.
     * @param deleteTarget True if the target volume should be deleted.
     * @param stepId The id of the WF step in which the target is being unlinked.
     */
    public BlockSnapshotSessionUnlinkTargetCompleter(URI snapSessionURI, URI snapshotURI, Boolean deleteTarget, String stepId) {
        super(BlockSnapshot.class, snapshotURI, stepId);
        _snapSessionURI = snapSessionURI;
        _deleteTarget = deleteTarget;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            switch (status) {
                case error:
                    break;
                case ready:
                    // Remove the linked target from the linked targets for the session.
                    URI snapshotURI = getId();
                    String snapshotId = snapshotURI.toString();
                    BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, _snapSessionURI);
                    StringSet linkedTargets = snapSession.getLinkedTargets();
                    if ((linkedTargets != null) && (linkedTargets.contains(snapshotId))) {
                        linkedTargets.remove(snapshotId);
                        dbClient.persistObject(snapSession);
                    }

                    // If a target is deleted, mark the associated BlockSnapshot inactive.
                    if (_deleteTarget) {
                        BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotURI);
                        snapshot.setInactive(true);
                        dbClient.persistObject(snapshot);
                    }
                    break;
                default:
                    String errMsg = String.format("Unexpected status %s for completer for step %s", status.name(), getOpId());
                    s_logger.info(errMsg);
                    throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done unlink targets from snapshot session step {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for unlink targets from snapshot session step {}", getOpId(), e);
        }
    }

    /**
     * Gets if the target is to be deleted.
     * 
     * @return true if the target is to be deleted, false otherwise.
     */
    public boolean getDeleteTarget() {
        return _deleteTarget;
    }
}
