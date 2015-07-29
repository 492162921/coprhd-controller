/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VNXeFileTaskCompleter extends TaskCompleter {

    private static final long serialVersionUID = 1L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeFileTaskCompleter.class);

    /**
     * @param clazz Class type
     * @param id
     * @param opId
     * @param operation operation type
     */
    public VNXeFileTaskCompleter(Class clazz, URI fsId, String opId) {
        super(clazz, fsId, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        _logger.info("VNXeTaskCompleter: set status to {}", status);
        if (isNotifyWorkflow()) {
            // If there is a workflow, update the step to complete.
            updateWorkflowStatus(status, coded);
        }
        super.setStatus(dbClient, status, coded);

    }

}
