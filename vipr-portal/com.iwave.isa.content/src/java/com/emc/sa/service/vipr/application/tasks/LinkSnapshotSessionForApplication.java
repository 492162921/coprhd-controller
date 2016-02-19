/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionLinkTargetsParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionRelinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionNewTargetsParam;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class LinkSnapshotSessionForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI snapSession;
    private final List<URI> linkedTargets;
    private final String copyMode;
    private final Integer count;
    private final String targetName;

    public LinkSnapshotSessionForApplication(URI applicationId, URI snapSession, List<URI> linkedTargets, String copyMode, Integer count,
            String targetName) {
        this.applicationId = applicationId;
        this.snapSession = snapSession;
        this.linkedTargets = linkedTargets;
        this.targetName = targetName;
        this.count = count;
        this.copyMode = copyMode;
        provideDetailArgs(applicationId, snapSession);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        TaskList taskList = null;

        if (linkedTargets != null && !linkedTargets.isEmpty()) {
            VolumeGroupSnapshotSessionRelinkTargetsParam relinkParam = new VolumeGroupSnapshotSessionRelinkTargetsParam();
            relinkParam.setLinkedTargetIds(linkedTargets);
            relinkParam.setPartial(true);
            relinkParam.setSnapshotSessions(Lists.newArrayList(snapSession));
            taskList = getClient().application().relinkApplicationSnapshotSession(applicationId, relinkParam);
        } else {
            VolumeGroupSnapshotSessionLinkTargetsParam input = new VolumeGroupSnapshotSessionLinkTargetsParam();
            input.setSnapshotSessions(Lists.newArrayList(snapSession));
            input.setPartial(true);
            SnapshotSessionNewTargetsParam newLinkedTargets = new SnapshotSessionNewTargetsParam();
            newLinkedTargets.setCopyMode(copyMode);
            newLinkedTargets.setCount(count);
            newLinkedTargets.setTargetName(targetName);
            input.setNewLinkedTargets(newLinkedTargets);
            taskList = getClient().application().linkApplicationSnapshotSession(applicationId, input);
        }

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
