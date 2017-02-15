/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.emc.sa.service.vipr.block.ExportVMwareBlockVolumeHelper;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.block.export.VolumeUpdateParam;
import com.emc.vipr.client.Task;

public class AddVolumesToExport extends WaitForTask<ExportGroupRestRep> {
    private final URI exportId;
    private final Collection<URI> volumeIds;
    private final Integer hlu;
    private final Map<URI, Integer> volumeHlus;
    private final Integer minPaths;
    private final Integer maxPaths;
    private final Integer pathsPerInitiator;
    private final URI portGroup;

    public AddVolumesToExport(URI exportId, Collection<URI> volumeIds, Integer hlu, Map<URI, Integer> volumeHlus, Integer minPaths,
            Integer maxPaths, Integer pathsPerInitiator, URI portGroup) {
        super();
        this.exportId = exportId;
        this.volumeIds = volumeIds;
        this.hlu = hlu;
        this.volumeHlus = volumeHlus;
        this.minPaths = minPaths;
        this.maxPaths = maxPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.portGroup = portGroup;
        provideDetailArgs(exportId, volumeIds, hlu);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportUpdateParam export = new ExportUpdateParam();
        List<VolumeParam> volumes = new ArrayList<VolumeParam>();
        Integer currentHlu = hlu;
        for (URI volumeId : volumeIds) {
            VolumeParam volume = new VolumeParam(volumeId);
            if (currentHlu != null) {
                if (currentHlu.equals(ExportVMwareBlockVolumeHelper.USE_EXISTING_HLU) && volumeHlus != null) {
                    Integer volumeHlu = volumeHlus.get(volume.getId());
                    if (volumeHlu == null) {
                        volume.setLun(-1);
                    } else {
                        volume.setLun(volumeHlu);
                    }
                } else {
                    volume.setLun(currentHlu);
                }
            }
            if ((currentHlu != null) && (currentHlu > -1)) {
                currentHlu++;
            }
            volumes.add(volume);
        }
        export.setVolumes(new VolumeUpdateParam(volumes, new ArrayList<URI>()));

        ExportPathParameters exportPathParameters = new ExportPathParameters();
        if (minPaths != null && maxPaths != null && pathsPerInitiator != null) {
            exportPathParameters.setMinPaths(minPaths);
            exportPathParameters.setMaxPaths(maxPaths);
            exportPathParameters.setPathsPerInitiator(pathsPerInitiator);
        }
        if (portGroup != null ) {
            exportPathParameters.setPortGroup(portGroup);
        }
        export.setExportPathParameters(exportPathParameters);

        return getClient().blockExports().update(exportId, export);
    }
}
