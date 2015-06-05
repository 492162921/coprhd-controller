/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.windows;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;
import com.iwave.ext.windows.WindowsSystemWinRM;
import com.iwave.ext.windows.model.Disk;
import com.iwave.ext.windows.model.Volume;
import com.iwave.ext.windows.model.wmi.DiskDrive;

public class UnmountBlockVolumeHelper {
    
    private final WindowsSupport windows;

    /** The volumes to unmount. */
    private Collection<? extends BlockObjectRestRep> volumes;
    
    /** Mapping of volume to disk. */
    private Map<? extends BlockObjectRestRep, DiskDrive> volume2disk;

    public static List<UnmountBlockVolumeHelper> createHelpers(List<WindowsSystemWinRM> windowsSystems) {
        List<UnmountBlockVolumeHelper> helpers = Lists.newArrayList();
        for (WindowsSystemWinRM windowsSystem : windowsSystems) {
            WindowsSupport windowsSupport = new WindowsSupport(windowsSystem);
            UnmountBlockVolumeHelper unmountBlockVolumeHelper = new UnmountBlockVolumeHelper(windowsSupport);
            BindingUtils.bind(unmountBlockVolumeHelper, ExecutionUtils.currentContext().getParameters());
            helpers.add(unmountBlockVolumeHelper);
        }

        return helpers;
    }

    private UnmountBlockVolumeHelper(WindowsSupport windowsSupport) {
        this.windows = windowsSupport;
    }

    public void setVolumes(Collection<? extends BlockObjectRestRep> volumes) {
        this.volumes = volumes;
    }

    public void precheck() {
        windows.verifyWinRM();
        windows.verifyVolumesMounted(volumes);
        volume2disk = windows.findDisks(volumes);
    }

    public void removeVolumesFromCluster() {
        Map<String, String> diskToResourceMap = windows.getDiskToResourceMap();

        for (BlockObjectRestRep volume : volumes) {
            DiskDrive diskDrive = volume2disk.get(volume);
            Disk diskDetail = windows.getDiskDetail(diskDrive);
            String resourceName = "";
            if (windows.isGuid(diskDetail.getDiskId())) {
                resourceName = diskToResourceMap.get(diskDetail.getDiskId());
            } else {            
                resourceName = diskToResourceMap.get(diskDrive.getSignature());
            }
            
            windows.offlineClusterResource(resourceName);
            windows.deleteClusterResource(resourceName);
        }
    }

    public void unmountVolumes() {
        for (BlockObjectRestRep volume : volumes) {
            DiskDrive disk = volume2disk.get(volume);
            Disk diskDetail = windows.getDiskDetail(disk);

            if (diskDetail.getVolumes() != null) {
                for (Volume diskVolume : diskDetail.getVolumes()) {
                    windows.unmountVolume(diskVolume.getNumber(), diskVolume.getMountPoint());
                    boolean isDriveLetterOnly = WindowsUtils.isMountPointDriveLetterOnly(diskVolume.getMountPoint()) ;
                    if (!isDriveLetterOnly && windows.isDirectoryEmpty(diskVolume.getMountPoint())) {
                        windows.deleteDirectory(diskVolume.getMountPoint());
                    }
                }
            }

            windows.offlineDisk(disk);
            windows.removeVolumeMountPoint(volume);
        }
    }
}
