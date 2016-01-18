package com.emc.sa.service.vipr.migration;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.GetMobilityGroup;
import com.emc.sa.service.vipr.block.tasks.GetMobilityGroupVolumes;
import com.emc.sa.service.vipr.block.tasks.MigrateBlockVolumes;
import com.emc.sa.service.vipr.block.tasks.RemoveVolumesFromMobilityGroup;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

@Service("MobilityGroupMigration")
public class MobilityGroupMigrationService extends ViPRService {

    @Param(ServiceParams.MOBILITY_GROUP)
    private URI mobilityGroupId;

    @Param(ServiceParams.TARGET_VIRTUAL_POOL)
    private URI targetVirtualPool;

    @Param(ServiceParams.TARGET_STORAGE_SYSTEM)
    private URI targetStorageSystem;

    private VolumeGroupRestRep mobilityGroup;

    @Override
    public void precheck() throws Exception {
        mobilityGroup = execute(new GetMobilityGroup(mobilityGroupId));
    }

    @Override
    public void execute() throws Exception {
        Tasks<VolumeRestRep> tasks = execute(new MigrateBlockVolumes(getVolumes(), mobilityGroup.getSourceStorageSystem(),
                targetVirtualPool, targetStorageSystem));

        if (!tasks.getTasks().isEmpty()) {
            execute(new RemoveVolumesFromMobilityGroup(mobilityGroup.getId(), getVolumeList(tasks)));
            addAffectedResources(tasks);
        } else {
            ExecutionUtils.fail("failTask.mobilityGroupMigration.noVolumesMigrated", new Object[] {}, new Object[] {});
        }

    }

    private List<URI> getVolumeList(Tasks<VolumeRestRep> tasks) {
        List<URI> volumes = Lists.newArrayList();
        for (Task<VolumeRestRep> task : tasks.getTasks()) {

            if (task.getResourceId() != null) {
                volumes.add(task.getResourceId());
            }
        }
        return volumes;
    }

    private List<URI> getVolumes() {
        if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.VOLUME.name())) {
            return execute(new GetMobilityGroupVolumes(mobilityGroupId));
        }
        return Lists.newArrayList();
    }
}
