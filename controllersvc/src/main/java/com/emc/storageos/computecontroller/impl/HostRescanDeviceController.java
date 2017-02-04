/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.util.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.aix.AixSystem;
import com.emc.hpux.HpuxSystem;
import com.emc.storageos.computecontroller.HostRescanController;
import com.emc.storageos.computesystemcontroller.impl.adapter.EsxHostDiscoveryAdapter;
import com.emc.storageos.computesystemcontroller.impl.adapter.VcenterDiscoveryAdapter;
import com.emc.storageos.computesystemcontroller.impl.adapter.WindowsHostDiscoveryAdapter;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.util.KerberosUtil;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.iwave.ext.command.HostRescanAdapter;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.mo.HostSystem;

public class HostRescanDeviceController implements HostRescanController {
    private DbClient dbClient;
    private static final Logger log = LoggerFactory.getLogger(HostRescanDeviceController.class);

    public Method rescanHostStorageMethod(URI hostId) {
        return new Method("rescanHostStorage", hostId);
    }
    
    @Override
    public void rescanHostStorage(URI hostId, String taskId) {
        try {
        // Set Workflow Step as Executing
        WorkflowStepCompleter.stepExecuting(taskId);
        Host host = dbClient.queryObject(Host.class, hostId);
        if (host == null || host.getInactive()) {
            throw DeviceControllerException.exceptions.objectNotFound(hostId);
        }
        Log.info(String.format("Initiating rescan on host %s (%s) type %s", host.getHostName(), host.getId(), host.getType()));
        HostRescanAdapter adapter = getRescanAdapter(host);
        if (adapter == null) {
            throw DeviceControllerException.exceptions.hostRescanUnsuccessful(host.getHostName(), 
                    "Could not get adapter to connect to Host");
        }
        log.info(String.format("Rescanning Host %s", host.getHostName()));
        adapter.rescan();
        WorkflowStepCompleter.stepSucceeded(taskId, String.format("Rescan complete host %s", host.getHostName()));
        
        } catch (DeviceControllerException ex) {
            log.info(String.format("Exception trying to rescan host %s : %s", hostId, ex.getMessage()));
            WorkflowStepCompleter.stepFailed(taskId, ex);
        } catch (Exception ex) {
            log.info(String.format("Exception trying to rescan host %s : %s", hostId, ex.getMessage(), ex));
            WorkflowStepCompleter.stepFailed(taskId, 
                    DeviceControllerException.exceptions.hostRescanUnsuccessful(hostId.toString(), "Unanticipated exception"));
        }
    }
    
    private HostRescanAdapter getRescanAdapter(Host host) {
        if (host.getType().equalsIgnoreCase(HostType.Linux.name())) {
            return new LinuxSystemCLI(host.getHostName(), host.getPortNumber(), host.getUsername(), host.getPassword());
        } else if (host.getType().equalsIgnoreCase(HostType.AIX.name())) {
            return new AixSystem(host.getHostName(), host.getPortNumber(), host.getUsername(), host.getPassword());
        } else if (host.getType().equalsIgnoreCase(HostType.HPUX.name())) {
            return new HpuxSystem(host.getHostName(), host.getPortNumber(), host.getUsername(), host.getPassword());
        } else if (host.getType().equalsIgnoreCase(HostType.Windows.name())) {
            List<AuthnProvider> authProviders = new ArrayList<AuthnProvider>();
            for (URI authProviderId : getDbClient().queryByType(AuthnProvider.class, true)) {
                 AuthnProvider authProvider = getDbClient().queryObject(AuthnProvider.class, authProviderId);
                 authProviders.add(authProvider);
            }
            KerberosUtil.initializeKerberos(authProviders);
            return WindowsHostDiscoveryAdapter.createWindowsSystem(host);
        } else if (host.getType().equalsIgnoreCase(HostType.Esx.name())) {
            if (host.getUsername() != null && host.getPassword() != null) {
                VCenterAPI vcenterAPI = EsxHostDiscoveryAdapter.createVCenterAPI(host);
                List<HostSystem> hostSystems = vcenterAPI.listAllHostSystems();
                if (hostSystems != null && !hostSystems.isEmpty()) {
                    return new HostStorageAPI(hostSystems.get(0));
                } else {
                    return null;
                }
            } else if (host.getVcenterDataCenter() != null){
                // Lookup the vcenter datacenter and vcenter to retreive the HostSystem
                VcenterDataCenter dataCenter = dbClient.queryObject(VcenterDataCenter.class, host.getVcenterDataCenter());
                if (dataCenter == null || dataCenter.getInactive()) {
                    throw DeviceControllerException.exceptions.objectNotFound(host.getVcenterDataCenter());
                }
                Vcenter vcenter = dbClient.queryObject(Vcenter.class, dataCenter.getVcenter());
                if (vcenter == null || vcenter.getInactive()) {
                    throw DeviceControllerException.exceptions.objectNotFound(dataCenter.getVcenter());
                }              
                VCenterAPI vCenterAPI = VcenterDiscoveryAdapter.createVCenterAPI(vcenter);
                String datacenterName = dataCenter.getLabel();
                HostSystem hostSystem = vCenterAPI.findHostSystem(datacenterName, host.getHostName());
                if (hostSystem != null) {
                    return new HostStorageAPI(hostSystem);
                } else {
                    return null;
                }
            }
        } else {
            // Unanticipated host type
            return null;
        }
        return null;
    }
    
    public Workflow.Method nullWorkflowStepMethod() {
        return new Method("nullWorkflowStep");
    }
    
    public void nullWorkflowStep(String taskId) {
        WorkflowStepCompleter.stepSucceded(taskId);
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

}
