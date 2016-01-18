/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.sa.asset.providers.BlockProviderUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class GetMobilityGroupVolumesByHost extends ViPRExecutionTask<List<URI>> {
    private final VolumeGroupRestRep mobilityGroup;
    private final List<NamedRelatedResourceRep> hosts;

    public GetMobilityGroupVolumesByHost(VolumeGroupRestRep mobilityGroup, List<NamedRelatedResourceRep> hosts) {
        this.mobilityGroup = mobilityGroup;
        this.hosts = hosts;
        provideDetailArgs(mobilityGroup, hosts);
    }

    @Override
    public List<URI> executeTask() throws Exception {
        List<URI> mobilityGroupVolumes = Lists.newArrayList();
        Set<URI> volumes = getHostExportedVolumes();

        for (URI volume : volumes) {
            VolumeRestRep vol = getClient().blockVolumes().get(volume);
            if (matchesVirtualPool(vol) && hasHaVolumes(vol)) {
                for (RelatedResourceRep haVolume : vol.getHaVolumes()) {
                    if (matchesStorageSystem(haVolume)) {
                        mobilityGroupVolumes.add(volume);
                    }
                }
            }
        }
        return mobilityGroupVolumes;
    }

    private boolean matchesStorageSystem(RelatedResourceRep haVolume) {
        VolumeRestRep haVol = getClient().blockVolumes().get(haVolume.getId());
        return haVol.getStorageController().equals(mobilityGroup.getSourceStorageSystem());
    }

    private boolean matchesVirtualPool(VolumeRestRep vol) {
        return vol.getVirtualPool().getId() != null && vol.getVirtualPool().getId().equals(mobilityGroup.getSourceVirtualPool());
    }

    private boolean hasHaVolumes(VolumeRestRep vol) {
        return vol.getHaVolumes() != null && !vol.getHaVolumes().isEmpty();
    }

    private Set<URI> getHostExportedVolumes() {
        Set<URI> volumes = Sets.newHashSet();
        for (NamedRelatedResourceRep host : hosts) {
            List<ExportGroupRestRep> exports = getClient().blockExports().findContainingHost(host.getId());
            volumes.addAll(BlockProviderUtils.getExportedResourceIds(exports, ResourceType.VOLUME));
        }
        return volumes;
    }
}
