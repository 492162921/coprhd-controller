/*
 * Copyright (c) 2015-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.isilon.job;

import java.net.URI;

import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;



public class IsilonSyncJobStart extends IsilonSyncIQJob {
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        super.updateStatus(jobContext);
    }

    public IsilonSyncJobStart(String jobId, URI sourceSystemUri, URI targetSystemUri, TaskCompleter taskCompleter, String jobName) {
        super(jobId, sourceSystemUri, targetSystemUri, taskCompleter, jobName);
    }
    
    public IsilonSyncJobStart(String jobId, URI sourceSystemUri, TaskCompleter taskCompleter, String jobName) {
        super(jobId, sourceSystemUri, taskCompleter, jobName);
    }
}
