/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

public class AuditBlockUtil {

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(AuditBlockUtil.class);

    /**
     * Record audit log for block devices
     * @param auditType Type of AuditLog
     * @param operationalStatus success or failure
     * @param operation stage   
     *          For sync operation, it should be null;
     *          For async operation, it should be "BEGIN" or "END";
     * @param descparams Description paramters
     */
    public static void auditBlock(DbClient dbClient, OperationTypeEnum auditType,
    		boolean operationalStatus, String operationStage, Object... descparams) {
        AuditLogManager auditMgr = new AuditLogManager();
        auditMgr.setDbClient(dbClient);
        auditMgr.recordAuditLog(null,
                null,
                ControllerUtils.BLOCK_EVENT_SERVICE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus? AuditLogManager.AUDITLOG_SUCCESS : AuditLogManager.AUDITLOG_FAILURE,
                operationStage, 
                descparams);
    }
}
