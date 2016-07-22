package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.file.MountInfo;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.google.common.collect.Lists;
import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.command.AddToFSTabCommand;
import com.iwave.ext.linux.command.DeleteDirectoryCommand;
import com.iwave.ext.linux.command.ListMountPointsCommand;
import com.iwave.ext.linux.command.MkdirCommand;
import com.iwave.ext.linux.command.MountCommand;
import com.iwave.ext.linux.command.RemoveFromFSTabCommand;
import com.iwave.ext.linux.command.UnmountCommand;
import com.iwave.ext.linux.model.MountPoint;

public class MountUtils {
    private LinuxSystemCLI cli;
    private Host host;

    public MountUtils(Host host) {
        cli = createLinuxCLI(host);
        this.host = host;
    }

    public MountUtils() {

    }

    public LinuxSystemCLI getCli() {
        return cli;
    }

    public void setCli(LinuxSystemCLI cli) {
        this.cli = cli;
    }

    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public void mountPath(String path) throws InternalException {
        MountCommand command = new MountCommand();
        command.setPath(path);
        cli.executeCommand(command);
    }

    public void createDirectory(String path) throws InternalException {
        MkdirCommand command = new MkdirCommand(true);
        command.setDir(path);
        cli.executeCommand(command);
    }

    public void addToFSTab(String path, String device, String fsType, String options) throws InternalException {
        AddToFSTabCommand command = new AddToFSTabCommand();
        command.setOptions(device, path, fsType, options);
        cli.executeCommand(command);
    }

    public void deleteDirectory(String directory) throws InternalException {
        DeleteDirectoryCommand command = new DeleteDirectoryCommand(directory, true);
        cli.executeCommand(command);
    }

    public Boolean isDirectoryEmpty(String directory) throws InternalException {
        String command = "ls " + directory;
        CommandOutput output = cli.executeCommand(command);
        if (StringUtils.isBlank(output.getStdout())) {
            return Collections.emptyList().isEmpty();
        } else {
            return Lists.newArrayList(output.getStdout().split("\n")).isEmpty();
        }
    }

    public void removeFromFSTab(String path) throws InternalException {
        RemoveFromFSTabCommand command = new RemoveFromFSTabCommand();
        command.setMountPoint(path);
        cli.executeCommand(command);
    }

    public void unmountPath(String path) throws InternalException {
        UnmountCommand command = new UnmountCommand();
        command.setPath(path);
        cli.executeCommand(command);
    }

    public void verifyMountPoint(String mountPoint) throws InternalException {
        if (!StringUtils.startsWith(mountPoint, "/")) {
            throw new IllegalStateException("Mount Point not absolute: " + mountPoint);
        }
        checkExistingMountPoints(mountPoint);
    }

    protected void checkExistingMountPoints(String mountPoint) throws InternalException {
        ListMountPointsCommand command = new ListMountPointsCommand();
        cli.executeCommand(command);
        Map<String, MountPoint> mountPoints = command.getResults();
        for (MountPoint mp : mountPoints.values()) {
            if (StringUtils.equals(mp.getPath(), mountPoint)) {
                throw new IllegalStateException("Mount point already exists: " + mountPoint);
            }
        }
    }

    public static LinuxSystemCLI convertHost(Host host) {
        LinuxSystemCLI cli = new LinuxSystemCLI();
        cli.setHost(host.getHostName());
        cli.setPort(host.getPortNumber());
        cli.setUsername(host.getUsername());
        cli.setPassword(host.getPassword());
        cli.setHostId(host.getId());
        return cli;
    }

    public static LinuxSystemCLI createLinuxCLI(Host host) {
        if ((host.getPortNumber() != null) && (host.getPortNumber() > 0)) {
            return new LinuxSystemCLI(host.getHostName(), host.getPortNumber(), host.getUsername(), host.getPassword());
        } else {
            return new LinuxSystemCLI(host.getHostName(), host.getUsername(), host.getPassword());
        }
    }

    public String generateMountTag(URI hostId, String mountPath, String subDirectory, String securityType) {
        return "mountNFS;" + hostId.toString() + ";" + mountPath + ";" + subDirectory + ";" + securityType;
    }

    public List<MountInfo> convertNFSTagsToMounts(List<String> mountTags) {
        List<MountInfo> mountList = new ArrayList<MountInfo>();
        for (String tag : mountTags) {
            mountList.add(convertNFSTag(tag));
        }
        return mountList;
    }

    public static MountInfo convertNFSTag(String tag) {
        MountInfo mountInfo = new MountInfo();
        if (tag.startsWith("mountNFS")) {
            String[] pieces = StringUtils.trim(tag).split(";");
            if (pieces.length > 1) {
                mountInfo.setHostId(URIUtil.uri(pieces[1]));
            }
            if (pieces.length > 2) {
                mountInfo.setMountPath(pieces[2]);
            }
            if (pieces.length > 3) {
                mountInfo.setSubDirectory(pieces[3]);
            }
            if (pieces.length > 4) {
                mountInfo.setSecurityType(pieces[4]);
            }
            if (pieces.length > 5) {
                mountInfo.setFsId(URIUtil.uri(pieces[5]));
            }
            mountInfo.setTag(tag);
        }
        return mountInfo;
    }
}
