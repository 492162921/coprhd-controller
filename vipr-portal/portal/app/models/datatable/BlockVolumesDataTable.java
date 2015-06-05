/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import util.datatable.DataTable;

import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep.SRDFRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.resources.BlockVolumes;

public class BlockVolumesDataTable extends DataTable {

    public BlockVolumesDataTable() {
        addColumn("name");
        addColumn("capacity");
        addColumn("varray");
        addColumn("vpool");
        addColumn("protocols");
        sortAll();
        setDefaultSort("name", "asc");

        setRowCallback("createRowLink");
    }

    public static List<Volume> fetch(URI projectId) {
        if (projectId == null) {
            return Collections.EMPTY_LIST;
        }

        ViPRCoreClient client = getViprClient();
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(projectId);
        Map<URI,String> virtualArrays = ResourceUtils.mapNames(client.varrays().list());
        Map<URI,String> virtualPools = ResourceUtils.mapNames(client.blockVpools().list());

        List<Volume> results = Lists.newArrayList();
        for (VolumeRestRep volume : volumes) {
            results.add(new Volume(volume, virtualArrays, virtualPools));
        }
        return results;
    }

    public static class Volume {
    	public String rowLink;
        public URI id;
        public String name;
        public String capacity;
        public String varray;
        public String vpool;
        public Set<String> protocols;
        public boolean srdfTarget;

        public Volume(VolumeRestRep volume, Map<URI,String> varrayMap, Map<URI,String> vpoolMap) {
            id = volume.getId();
            name = volume.getName();
            srdfTarget = volume.getProtection() != null && volume.getProtection().getSrdfRep() != null && volume.getProtection().getSrdfRep().getAssociatedSourceVolume() !=null;
            this.rowLink = createLink(BlockVolumes.class, "volume", "volumeId", id);
            capacity = volume.getProvisionedCapacity();
            if (volume.getVirtualArray() != null) {
                varray = varrayMap.get(volume.getVirtualArray().getId());
            }
            if (volume.getVirtualPool() != null) {
                vpool = vpoolMap.get(volume.getVirtualPool().getId());
            }
            protocols = volume.getProtocols();
        }
    }
}
