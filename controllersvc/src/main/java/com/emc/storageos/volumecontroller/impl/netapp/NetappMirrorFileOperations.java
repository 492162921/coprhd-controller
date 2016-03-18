package com.emc.storageos.volumecontroller.impl.netapp;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.netapp.NetAppApi;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorOperations;

public class NetappMirrorFileOperations implements FileMirrorOperations {
    private static final Logger _log = LoggerFactory
            .getLogger(NetappMirrorFileOperations.class);

    private DbClient _dbClient;

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    public NetappMirrorFileOperations() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub
        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, source);
        FileShare targetFileShare = _dbClient.queryObject(FileShare.class, target);

        StorageSystem sourceStorageSystem = _dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());
        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

        BiosCommandResult cmdResult = null;
        // send netapp api call
        String portGroup = findVfilerName(sourceFileShare);
        VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());

    }

    @Override
    public void stopMirrorFileShareLink(StorageSystem sourceStorage, FileShare targetFs, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, targetFs.getParentFileShare().getURI());
        StorageSystem targetStorage = _dbClient.queryObject(StorageSystem.class, targetFs.getStorageDevice());

        String portGroup = findVfilerName(sourceFileShare);
        BiosCommandResult cmdResult = doReleaseSnapMirror(sourceStorage, sourceFileShare, completer);
        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void
            startMirrorFileShareLink(StorageSystem sourceStorage, FileShare targetFileShare, TaskCompleter completer, String policyName)
                    throws DeviceControllerException {

        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, targetFileShare.getParentFileShare().getURI());
        StorageSystem targetStorageSystem = _dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());
        VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());

        BiosCommandResult cmdResult = doInitializeSnapMirror(sourceStorage, targetStorageSystem,
                sourceFileShare, targetFileShare, completer);

        // set the schedule time
        if (cmdResult.getCommandSuccess()) {
            cmdResult = setScheduleSnapMirror(sourceStorage, targetStorageSystem,
                    sourceFileShare, targetFileShare);
            if (cmdResult.getCommandSuccess()) {
                completer.ready(_dbClient);
            } else {
                completer.error(_dbClient, cmdResult.getServiceCoded());
            }

        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void failoverMirrorFileShareLink(StorageSystem targetSystem, FileShare targetFileShare, TaskCompleter completer,
            String policyName)
            throws DeviceControllerException {

        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, targetFileShare.getParentFileShare().getURI());
        StorageSystem sourceStorage = _dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());

        BiosCommandResult cmdResult = doFailoverSnapMirror(sourceStorage, targetSystem, sourceFileShare, targetFileShare, completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }

    }

    @Override
    public void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare fileshare,
            TaskCompleter completer, String policyName) {
        // TODO Auto-generated method stub
        FileShare source = null;
        FileShare target = null;
        if (fileshare.getParentFileShare() == null) {
            source = fileshare;
            // we have fix this target issue
        } else {
            source = _dbClient.queryObject(FileShare.class, fileshare.getParentFileShare().getURI());
            target = fileshare;
        }

        String portGroup = findVfilerName(fileshare);
        BiosCommandResult cmdResult = doResyncSnapMirror(primarySystem, portGroup, source.getMountPath(), target.getPath(), completer);

        if (cmdResult.getCommandSuccess()) {
            completer.ready(_dbClient);
        } else {
            completer.error(_dbClient, cmdResult.getServiceCoded());
        }
    }

    @Override
    public void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void cancelMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void refreshMirrorFileShareLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    /**
     * Return the vFiler name associated with the file system. If a vFiler is not associated with
     * this file system, then it will return null.
     */
    private String findVfilerName(FileShare fs) {
        String portGroup = null;

        URI port = fs.getStoragePort();
        if (port == null) {
            _log.info("No storage port URI to retrieve vFiler name");
        } else {
            StoragePort stPort = _dbClient.queryObject(StoragePort.class, port);
            if (stPort != null) {
                URI haDomainUri = stPort.getStorageHADomain();
                if (haDomainUri == null) {
                    _log.info("No Port Group URI for port {}", port);
                } else {
                    StorageHADomain haDomain = _dbClient.queryObject(StorageHADomain.class, haDomainUri);
                    if (haDomain != null && haDomain.getVirtual() == true) {
                        portGroup = stPort.getPortGroup();
                        _log.debug("using port {} and vFiler {}", stPort.getPortNetworkId(), portGroup);
                    }
                }
            }
        }
        return portGroup;
    }

    BiosCommandResult doCreateSnapMirror(StorageSystem storage, String portGroup, String sourcePath, String destPath) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.createSnapMirror(sourcePath, sourcePath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult doInitializeSnapMirror(StorageSystem sourceStorage, StorageSystem targetStorage, FileShare sourceFs,
            FileShare targetFs, TaskCompleter taskCompleter) {
        // netapp source client
        String portGroupSource = findVfilerName(sourceFs);
        NetAppApi nApiSource = new NetAppApi.Builder(sourceStorage.getIpAddress(),
                sourceStorage.getPortNumber(), sourceStorage.getUsername(),
                sourceStorage.getPassword()).https(true).vFiler(portGroupSource).build();

        // get source system name
        String sourceLocation = getLocation(nApiSource, sourceFs);

        // target netapp
        String portGroupTarget = findVfilerName(targetFs);
        NetAppApi nApiTarget = new NetAppApi.Builder(targetStorage.getIpAddress(),
                targetStorage.getPortNumber(), sourceStorage.getUsername(),
                targetStorage.getPassword()).https(true).vFiler(portGroupTarget).build();

        String destLocation = getLocation(nApiTarget, sourceFs);

        // make api call
        nApiTarget.initializeSnapMirror(sourceLocation, destLocation, portGroupTarget);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult doReleaseSnapMirror(StorageSystem storage, FileShare share,
            TaskCompleter taskCompleter) {

        String portGroup = findVfilerName(share);
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        String location = getLocation(nApi, share);
        nApi.releaseSnapMirror(location, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult doResyncSnapMirror(StorageSystem storage, String portGroup, String sourcePath, String destPath,
            TaskCompleter taskCompleter) {
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();
        // make api call
        nApi.resyncSnapMirror(sourcePath, destPath, portGroup);
        return BiosCommandResult.createSuccessfulResult();
    }

    public BiosCommandResult
            doFailoverSnapMirror(StorageSystem sourceStorage, StorageSystem targetStorage, FileShare sourceFs,
                    FileShare targetFs, TaskCompleter taskCompleter) {
        // get vfiler
        String portGroupTarget = findVfilerName(targetFs);
        NetAppApi nApi = new NetAppApi.Builder(targetStorage.getIpAddress(),
                targetStorage.getPortNumber(), targetStorage.getUsername(),
                targetStorage.getPassword()).https(true).vFiler(portGroupTarget).build();
        // make api call
        String destLocation = getLocation(targetStorage, targetFs);
        nApi.breakSnapMirror(destLocation, portGroupTarget);
        return BiosCommandResult.createSuccessfulResult();
    }

    /**
     * 
     * @param storage -target file system
     * @param portGroup - vfiler name
     * @param vPool - Virtual pool
     * @param sourcePath - source location
     * @param targetPath - destination location
     * @return
     */
    public BiosCommandResult setScheduleSnapMirror(StorageSystem sourceStorage, StorageSystem targetStorage, FileShare sourceFs,
            FileShare targetFs) {
        VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, sourceFs.getVirtualPool());
        Long rpo = vPool.getFrRpoValue();
        String rpoType = "days-of-week";
        String rpoValue = "-";

        switch (vPool.getFrRpoType()) {
            case "MINUTES":
                rpoType = "minutes";
                rpoValue = rpoType;
                break;
            case "HOURS":
                rpoType = "hours";
                rpoValue = rpoType;
                break;
            case "DAYS":
                rpoType = "days-of-month";
                rpoValue = rpoType;
                break;
        }

        String destLocation = getLocation(targetStorage, targetFs);

        // make api call on source to set schedule
        String portGroupSource = findVfilerName(sourceFs);
        NetAppApi nApiSource = new NetAppApi.Builder(sourceStorage.getIpAddress(),
                sourceStorage.getPortNumber(), sourceStorage.getUsername(),
                sourceStorage.getPassword()).https(true).vFiler(portGroupSource).build();
        String sourceLocation = getLocation(nApiSource, sourceFs);

        nApiSource.setScheduleSnapMirror(rpoType, rpoValue, sourceLocation, destLocation, portGroupSource);
        return BiosCommandResult.createSuccessfulResult();
    }

    public String getLocation(NetAppApi nApi, FileShare share) {
        StringBuilder builderLoc = new StringBuilder();

        Map<String, String> systeminfo = nApi.systemInfo();
        String systemTargetName = systeminfo.get("system-name");

        builderLoc.append(systemTargetName);
        builderLoc.append(":");
        builderLoc.append(share.getName());
        return builderLoc.toString();
    }

    public String getLocation(StorageSystem storage, FileShare share) {
        String portGroup = findVfilerName(share);
        NetAppApi nApi = new NetAppApi.Builder(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword()).https(true).vFiler(portGroup).build();

        return getLocation(nApi, share);

    }

}
