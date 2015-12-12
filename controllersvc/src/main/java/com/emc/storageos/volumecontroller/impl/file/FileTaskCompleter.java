/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.FileShare;

import com.emc.storageos.volumecontroller.impl.block.taskcompleter.TaskLockingCompleter;
import com.emc.storageos.services.OperationTypeEnum;

public abstract class FileTaskCompleter extends TaskLockingCompleter {
	private static final String FILE_TASK_MSG_SUCCESS = "File operation completed successfully for filesystem %s";
    private static final String FILE_TASK_MSG_FAILURE = "File operation failed for filesystem %s";

    /**
     * Reference to logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(FileTaskCompleter.class);

    /**
     * @param clazz
     * @param id
     * @param opId
     */
    public FileTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    /**
     * @param clazz
     * @param fileUris
     * @param task
     */
    public FileTaskCompleter(Class<FileShare> clazz, List<URI> fileUris, String task) {
        super(clazz, fileUris, task);
    }

    /**
     * Generate and Record a file specific event
     * 
     * @param dbClient
     * @param id
     * @param evtType
     * @param status
     * @param desc
     * @throws Exception
     */
    public static void recordBourneFileEvent(DbClient dbClient,
            URI id, String evtType, Operation.Status status, String desc)
            throws Exception {
    	//TODO
    }

    /**
     * Record block file related event and audit
     * 
     * @param dbClient db client
     * @param opType operation type
     * @param status operation status
     * @param evDesc event description
     * @param extParam parameters array from which we could generate detail audit message
     */
    public void recordFileSystemOperation(DbClient dbClient, OperationTypeEnum opType, 
    		Operation.Status status, Object... extParam) {
    	//TODO
    	
    }

    protected String eventMessage(Operation.Status status, FileShare fileShare) {
        return (status == Operation.Status.ready) ?
                String.format(FILE_TASK_MSG_SUCCESS, fileShare.getLabel()) :
                String.format(FILE_TASK_MSG_FAILURE, fileShare.getLabel());
    }
}
