/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.application.VolumeGroupUpdateParam.VolumeGroupVolumeList;
import com.emc.vipr.client.Tasks;

public class AddVolumesToApplication extends WaitForTasks<TaskResourceRep> {
    private final List<URI> volumeIds;
    private final URI applicationId;

    public AddVolumesToApplication(URI applicationId, List<URI> volumeIds) {
        this.volumeIds = volumeIds;
        this.applicationId = applicationId;
        provideDetailArgs(applicationId, volumeIds);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupUpdateParam input = new VolumeGroupUpdateParam();
        VolumeGroupVolumeList addVolumesList = new VolumeGroupVolumeList();
        addVolumesList.setVolumes(volumeIds);
        input.setAddVolumesList(addVolumesList);

        TaskList taskList = getClient().application().updateApplication(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
