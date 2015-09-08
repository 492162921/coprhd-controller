/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when a workflow restoring a BlockSnapshotSession completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionRestoreWorkflowCompleter extends BlockSnapshotSessionCompleter {

    // Message constants.
    public static final String SNAPSHOT_SESSION_RESTORE_SUCCESS_MSG = "Block Snapshot Session %s restored for source %s";
    public static final String SNAPSHOT_SESSION_RESTORE_FAIL_MSG = "Failed to restore Block Snapshot Session %s for source %s";

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionRestoreWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionRestoreWorkflowCompleter(URI snapSessionURI, String taskId) {
        super(snapSessionURI, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        URI snapSessionURI = getId();
        try {
            BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);
            BlockObject sourceObj = BlockObject.fetch(dbClient, snapSession.getParent().getURI());

            // Record the results.
            recordBlockSnapshotSessionOperation(dbClient, OperationTypeEnum.RESTORE_SNAPSHOT_SESSION,
                    status, snapSession, sourceObj);

            // Update the status map of the snapshot session.
            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, BlockSnapshotSession.class, snapSessionURI, coded);
                    break;
                case ready:
                    setReadyOnDataObject(dbClient, BlockSnapshotSession.class, snapSessionURI);
                    break;
                default:
                    String errMsg = String.format("Unexpected status %s for completer for task %s", status.name(), getOpId());
                    s_logger.info(errMsg);
                    throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the task to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done restore snapshot session task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for restore snapshot session task {}", getOpId(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_SESSION_RESTORE_SUCCESS_MSG, snapSession.getLabel(), sourceObj.getLabel()) :
                String.format(SNAPSHOT_SESSION_RESTORE_FAIL_MSG, snapSession.getLabel(), sourceObj.getLabel());
    }
}