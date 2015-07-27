/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.audit;

// Logger imports
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AuditLog;
import com.emc.storageos.db.client.model.AuditLogTimeSeries;
import com.emc.storageos.db.exceptions.DatabaseException;

import com.emc.storageos.services.OperationTypeEnum;

/**
 * AuditLogManager is used to store system auditlogs to the Cassandra database.
 */
public class AuditLogManager {

    final private Logger _log = LoggerFactory
                    .getLogger(AuditLogManager.class);

    private static final String PRODUCT_ID = "ViPR 1.0";

    public static final String AUDITLOG_SUCCESS = "SUCCESS";
    public static final String AUDITLOG_FAILURE = "FAILURE";

    public static final String AUDITOP_BEGIN = "BEGIN";
    public static final String AUDITOP_MULTI_BEGIN = "MULTI_BEGIN";
    public static final String AUDITOP_END = "END";

    // auditlog version, to compatible with the possible changes in the future.
    public static final String AUDITLOG_VERSION = "1";

    // A reference to the database client.
    private DbClient _dbClient;
    
    // The logger.
    private static Logger s_logger = LoggerFactory.getLogger(AuditLogManager.class);
    
    /**
     * Default constructor.
     */
    public AuditLogManager() {
    	super();
    }

    /**
     * Setter for the data base client.
     * 
     * @param dbClient Reference to a database client.
     */
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Called to record auditlogs in the database.
     * 
     * @param events references to recordable auditlogs.
     */
    public void recordAuditLogs(RecordableAuditLog... auditlogs) {
        AuditLog dbAuditLogs[] = new AuditLog[auditlogs.length];
        int i = 0;
        for (RecordableAuditLog auditlog:auditlogs) {
        	AuditLog dbAuditlog = AuditLogUtils.convertToAuditLog(auditlog);
            dbAuditLogs[i++] = dbAuditlog;
        }
        
        // Now insert the events into the database.
        try {
            String bucketId = _dbClient.insertTimeSeries(AuditLogTimeSeries.class, dbAuditLogs);
            s_logger.info("AuditLog(s) persisted into Cassandra with bucketId/rowId : {}", bucketId);
        } catch (DatabaseException e) {
            s_logger.error("Error inserting auditlogs into the database", e);
            throw e;
        }
    }

    /**
     * Record auditlog for the completed operations
     * @param tenantId      tenant URI
     * @param userId        user URI
     * @param serviceType   service type (e.g. CoS, Block etc.) 
     * @param auditType     audit event type (e.g. Create_COS|TEANT etc.)
     * @param timestamp     time that the audit event happened
     * @param operationalStatus result of the audit event
     * @param operationStage   
     *          a) For sync operation, it should be null;
     *          b) For async operation, it should be "BEGIN" or "END";     
     *        It is used as sub part of description Id. 
     *        The description Id which will be replaced with the 
     *        concrete description and parameters after fetching from db.
     * @param descparams    the parameters for the description.
     */
    public void recordAuditLog(URI tenantId,
            URI userId,
            String serviceType,
            OperationTypeEnum auditType,
            long timestamp,
            String operationalStatus,
            String operationStage,
            Object... descparams) {
        // Description Id which will be replaced with the concrete description and parameters after fetching from db.
        // Formatted description: "<auditlog version>|<description id>|<param1>|<param2>|..."
        // The formatted description will be persistent in cassandra db.  During query, it 
        // will be replaced with the real description in specific language.
        StringBuilder s = new StringBuilder(AUDITLOG_VERSION);
        s.append("|");
        s.append(auditType.toString());

        if (operationStage != null) {
            s.append("_");
            s.append(operationStage);
        }
        if (operationalStatus.equals(AUDITLOG_FAILURE)) {
            s.append("_");
            s.append(operationalStatus);
        }

        for (int i = 0; i < descparams.length; i++) {
            s.append("|");
            if (descparams[i] != null && !descparams[i].toString().isEmpty()) {
                s.append(descparams[i].toString());
            } else {
                s.append("null");
            }
        }

        RecordableAuditLog auditlog = new RecordableAuditLog(PRODUCT_ID,
                tenantId,
                userId,
                serviceType,
                auditType,
                timestamp,
                s.toString(),
                operationalStatus);
        try {
            recordAuditLogs(auditlog);
        } catch (Exception ex) {
            _log.error("Failed to record auditlog. Auditlog description id: {}. Error: {}.",
                    auditType.toString(), ex);
        }
    }
}
