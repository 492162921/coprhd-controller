/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import static com.emc.sa.service.ServiceParams.MULTIPATH_POLICY;
import static com.emc.sa.service.ServiceParams.STORAGE_IO_CONTROL;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.CreateBlockVolumeForHostHelper;
import com.emc.sa.service.vmware.VMwareBinding;
import com.emc.sa.service.vmware.VMwareBinding.DatastoreToVolumeParams;
import com.emc.sa.service.vmware.VMwareBinding.DatastoreToVolumeTable;
import com.emc.sa.service.vmware.VMwareHostService;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.mo.Datastore;

@Service("VMware-CreateVolumeAndVmfsDatastore")
public class CreateVolumeAndVmfsDatastoreService extends VMwareHostService {

    @Param(value = MULTIPATH_POLICY, required = false)
    protected String multipathPolicy;
    @Param(value = STORAGE_IO_CONTROL, required = false)
    protected Boolean storageIOControl;

    @Bindable(itemType = DatastoreToVolumeTable.class)
    protected DatastoreToVolumeTable[] datastoreToVolume;

    @Bindable
    protected DatastoreToVolumeParams datastoreToVolumeParams = new DatastoreToVolumeParams();

    List<String> datastoreNames = null;
    List<String> volumeNames = null;

    protected List<CreateBlockVolumeForHostHelper> createBlockVolumeHelpers = Lists.newArrayList();

    @Override
    public void init() throws Exception {
        super.init();

        int hluIncrement = 0;
        // for each pair of datastore / volume, bind params to createBlockVolumeHelper
        for (DatastoreToVolumeTable dsToVol : datastoreToVolume) {
            CreateBlockVolumeForHostHelper createBlockVolumeHelper = new CreateBlockVolumeForHostHelper();
            BindingUtils.bind(createBlockVolumeHelper,
                    VMwareBinding.createDatastoreVolumeParam(dsToVol, datastoreToVolumeParams, hluIncrement));
            createBlockVolumeHelpers.add(createBlockVolumeHelper);
            hluIncrement++;
        }
    }

    @Override
    public void precheck() throws Exception {
        datastoreNames = VMwareBinding.getDatastoreNamesFromDatastoreToVolume(datastoreToVolume);
        volumeNames = VMwareBinding.getVolumeNamesFromDatastoreToVolume(datastoreToVolume);

        if (datastoreNames.isEmpty()) {
            throw new IllegalStateException(
                    ExecutionUtils.getMessage("CreateVolumeAndVmfsDatastoreService.datastore.empty"));
        }

        if (!VMwareUtils.isUniqueNames(datastoreNames)) {
            throw new IllegalStateException(
                    ExecutionUtils.getMessage("CreateVolumeAndVmfsDatastoreService.datastore.datastore.notunique"));
        }

        if (!VMwareUtils.isUniqueNames(volumeNames)) {
            throw new IllegalStateException(
                    ExecutionUtils.getMessage("CreateVolumeAndVmfsDatastoreService.datastore.volume.notunique"));
        }

        super.precheck();
        for (CreateBlockVolumeForHostHelper helper : createBlockVolumeHelpers) {
            helper.precheck();
        }
        acquireHostLock();
        for (String datastore : datastoreNames) {
            vmware.verifyDatastoreDoesNotExist(datacenter.getLabel(), datastore);
        }
        vmware.verifySupportedMultipathPolicy(host, multipathPolicy);
        vmware.disconnect();
    }

    @Override
    public void execute() throws Exception {
        if (datastoreNames.size() != createBlockVolumeHelpers.size()) {
            throw new IllegalStateException(
                    ExecutionUtils.getMessage("CreateVolumeAndVmfsDatastoreService.datastore.volume.mismatch"));
        }

        int index = 0;
        List<URI> volumesToExport = Lists.newArrayList();
        Map<String, BlockObjectRestRep> datastoreVolumeMap = Maps.newHashMap();
        for (String datastoreName : datastoreNames) {
            List<URI> volumes = createBlockVolumeHelpers.get(index).createVolumes(createBlockVolumeHelpers.get(index).getComputeResource());
            volumesToExport.addAll(volumes);
            if (volumes.isEmpty()) {
                ExecutionUtils.fail("CreateVolumeAndVmfsDatastoreService.illegalState.noVolumesCreated", args(), args());
            }

            if (!volumes.isEmpty()) {
                BlockObjectRestRep volume = BlockStorageUtils.getBlockResource(volumes.get(0));
                datastoreVolumeMap.put(datastoreName, volume);
            }
            index++;
        }

        createBlockVolumeHelpers.get(0).exportVolumes(volumesToExport);

        connectAndInitializeHost();

        for (Entry<String, BlockObjectRestRep> t : datastoreVolumeMap.entrySet()) {
            Datastore datastore = vmware.createVmfsDatastore(host, cluster, hostId, t.getValue(), t.getKey());
            vmware.setMultipathPolicy(host, cluster, multipathPolicy, t.getValue());
            vmware.setStorageIOControl(datastore, storageIOControl);
        }

        // Refresh the storage on all hosts in the cluster after creating the datastores
        vmware.refreshStorage(host, cluster);
        vmware.disconnect();
    }
}
