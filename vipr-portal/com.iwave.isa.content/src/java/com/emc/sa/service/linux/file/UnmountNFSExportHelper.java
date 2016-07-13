/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.file;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.storageos.model.file.MountInfo;
import com.google.common.collect.Lists;
import com.iwave.ext.linux.LinuxSystemCLI;

public class UnmountNFSExportHelper {
    private final LinuxFileSupport linux;

    private List<MountInfo> mountList;

    public static UnmountNFSExportHelper createHelper(LinuxSystemCLI linuxSystem) {
        LinuxFileSupport linuxSupport = new LinuxFileSupport(linuxSystem);
        UnmountNFSExportHelper unmountNFSExportHelper = new UnmountNFSExportHelper(linuxSupport);
        BindingUtils.bind(unmountNFSExportHelper, ExecutionUtils.currentContext().getParameters());
        return unmountNFSExportHelper;
    }

    private UnmountNFSExportHelper(LinuxFileSupport linuxSupport) {
        this.linux = linuxSupport;
    }

    public void setMounts(List<MountInfo> mountList) {
        this.mountList = Lists.newArrayList();
        this.mountList.addAll(mountList);
    }

    public void unmountExports() {
        for (MountInfo mount : mountList) {
            logInfo("linux.mount.file.export.unmount", mount.getMountPoint(), linux.getHostName());
            // unmount the Export
            linux.unmountPath(mount.getMountPoint());
            // remove from fstab
            linux.removeFromFSTab(mount.getMountPoint());
            // delete the directory entry if it's empty
            if (linux.isDirectoryEmpty(mount.getMountPoint())) {
                linux.deleteDirectory(mount.getMountPoint());
            }
            linux.removeFSTag(mount.getFsId(), mount.getTag().substring(0, mount.getTag().lastIndexOf(";")));
        }
    }
}
