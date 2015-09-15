package models.datatable;

import static util.BourneUtil.getViprClient;

import java.util.Set;

import util.datatable.DataTable;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.vnas.VirtualNASRestRep;

public class VirtualNasServerDataTable extends DataTable {

    public VirtualNasServerDataTable() {
        addColumn("id").hidden();
        addColumn("nasName");
        addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        addColumn("project");
        addColumn("storageDeviceURI").hidden();
        addColumn("protocols");
        addColumn("parentNASURI");
        addColumn("storageDomain");
        addColumn("cifsServers").hidden();
        addColumn("storagePorts").hidden();
        addColumn("nasState");
        addColumn("compatibilityStatus").hidden();
        addColumn("discoveryStatus").hidden();
        addColumn("vNasTag").hidden();
        addColumn("vNasType").hidden();
        addColumn("baseDirPath").hidden();

        addColumn("storageObjects").hidden();
        addColumn("storageCapacity").hidden();
        addColumn("avgPercentagebusy").hidden();
        addColumn("percentLoad").hidden();

        sortAll();
        setDefaultSort("name", "asc");

    }

    public static class VirtualNasServerInfo {

        private final String id;
        // NAS Server name
        private final String nasName;

        // storageSystem, which it belongs
        private final String storageDeviceURI;
        private String maxFSID;
        private String maxExports;
        private String maxProvisionedCapacity;
        private final Set<String> protocols;

        // Set of Authentication providers for the VNasServer - set values will of type AunthnProvider
        private final Set<String> cifsServers;

        // List of Storage Ports associated with this Nas Server
        private final String storagePorts;

        // State of the NAS server
        private final String nasState;

        // Place holder for hosting storageDomain's information
        private final String storageDomain;

        private final String registrationStatus;
        private final String compatibilityStatus;
        private final String discoveryStatus;

        // Place holder for Tag
        private final Set<String> nasTag;

        // Project name which this VNAS belongs to
        private String project = "";

        private String vNasType;

        // Base directory Path for the VNAS applicable in AccessZones & vFiler device types
        private final String baseDirPath;

        // place holder for the Parent NAS server the Data Mover
        private String parentNASURI;

        private final String storageObjects;
        private final String storageCapacity;
        private final String avgPercentagebusy;
        private final String percentLoad;

        public VirtualNasServerInfo(VirtualNASRestRep vNasRestRep, boolean isProjectAccessible) {
            this.id = vNasRestRep.getId().toString();
            this.nasName = vNasRestRep.getNasName();
            this.storageDeviceURI = (vNasRestRep.getStorageDeviceURI() != null) ? vNasRestRep.getStorageDeviceURI().toString() : "";
            if (isProjectAccessible) {
                if (vNasRestRep.getProject() != null) {
                    ProjectRestRep projectRestRep = getViprClient().projects().get(vNasRestRep.getProject().getId());
                    this.project = projectRestRep.getName();
                }
            }

            this.protocols = vNasRestRep.getProtocols();
            this.baseDirPath = vNasRestRep.getBaseDirPath();
            this.nasTag = vNasRestRep.getTags();
            this.nasState = vNasRestRep.getNasState();
            this.cifsServers = vNasRestRep.getCifsServers();
            this.storagePorts = (vNasRestRep.getStoragePorts() != null) ? vNasRestRep.getStoragePorts().toString() : "";
            this.storageDomain = (vNasRestRep.getStorageDomain() != null) ? vNasRestRep.getStorageDomain().toString() : "";
            NamedRelatedResourceRep resourceRep = vNasRestRep.getParentNASURI();
            if (resourceRep != null) {
                this.parentNASURI = resourceRep.getName();
            }
            this.registrationStatus = vNasRestRep.getRegistrationStatus();
            this.compatibilityStatus = vNasRestRep.getCompatibilityStatus();
            this.discoveryStatus = vNasRestRep.getDiscoveryStatus();
            this.storageObjects = vNasRestRep.getStorageObjects();
            this.storageCapacity = vNasRestRep.getUsedStorageCapacity();
            this.avgPercentagebusy = vNasRestRep.getAvgPercentagebusy();
            this.percentLoad = vNasRestRep.getPercentLoad();

        }
    }

}