/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.vipr.client.Task;

public class DissociateFilePolicyFromFileSystem extends WaitForTask<FilePolicyRestRep> {

    private final URI fileSystemId;
    private final URI filePolicyId;

    public DissociateFilePolicyFromFileSystem(String fileSystemId, String filePolicyId) {
        this(uri(fileSystemId), uri(filePolicyId));
    }

    public DissociateFilePolicyFromFileSystem(URI fileSystemId, URI filePolicyId) {
        this.fileSystemId = fileSystemId;
        this.filePolicyId = filePolicyId;
        provideDetailArgs(fileSystemId, filePolicyId);
    }

    @Override
    protected Task<FilePolicyRestRep> doExecute() throws Exception {
        /*
         * FilePolicyUnAssignParam input = new FilePolicyUnAssignParam();
         * Set<URI> fileSystems = new HashSet<URI>();
         * fileSystems.add(fileSystemId);
         */
        // Get the task and covert it to PolicyRestRep
        TaskResourceRep task = getClient().fileSystems().dissociateFilePolicy(fileSystemId, filePolicyId);
        return getClient().fileProtectionPolicies().getTask(filePolicyId, task.getId());
    }
}
