package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.emc.vipr.client.exceptions.ServiceErrorException;

import util.datatable.DataTable;

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

        // whether this vnas is a replication destination remote or local
        private Boolean replicationDestination = false;

        // source vNas if replication destination
        private Set<String> sourceVirtualNasIds;

        // destination vNas if replication source
        private Set<String> destinationVirtualNasIds;

        public VirtualNasServerInfo(VirtualNASRestRep vNasRestRep, boolean isProjectAccessible) {

            StringBuffer projectListWithIds = new StringBuffer();
            this.nasName = vNasRestRep.getNasName();
            this.storageDeviceURI = (vNasRestRep.getStorageDeviceURI() != null) ? vNasRestRep.getStorageDeviceURI().toString() : "";
            if (isProjectAccessible) {
                Set<String> associatedProjects = vNasRestRep.getAssociatedProjects();
                if (associatedProjects != null && !associatedProjects.isEmpty()) {
                    StringBuffer projectList = new StringBuffer();

                    for (Iterator<String> iterator = associatedProjects.iterator(); iterator.hasNext();) {
                        String projectId = iterator.next();
                        try {
                            ProjectRestRep projectRestRep = getViprClient().projects().get(URI.create(projectId));
                            projectList.append(projectRestRep.getName()).append(", ");
                            projectListWithIds.append(projectRestRep.getName()).append("+").append(projectRestRep.getId()).append(",");
                        } catch (ServiceErrorException serviceErrorException) {
                            /*
                             * Check if the error is due to insufficient permissions
                             * Error code: 3000
                             */
                            if (serviceErrorException.getCode() == 3000) {
                                continue;
                            } else {
                                throw serviceErrorException;
                            }
                        }
                    }
                    if ((projectList.length() - 2) >= 0) {
                        this.project = projectList.substring(0, projectList.length() - 2);
                    }
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

            if (projectListWithIds.length() > 0) {
                this.id = vNasRestRep.getId().toString() + "~~~" + projectListWithIds.substring(0, projectListWithIds.length() - 1);
            } else {
                this.id = vNasRestRep.getId().toString() + "~~~";
            }
            // Setting Source and destination NAS names
            List<URI> nasList = vNasRestRep.getSourceVirtualNasIds();
            if (nasList != null) {
                if (this.sourceVirtualNasIds == null) {
                    this.sourceVirtualNasIds = new HashSet<String>();
                }
                for (URI nasUri : nasList) {
                    this.sourceVirtualNasIds.add(getViprClient().virtualNasServers().get(nasUri).getName());
                }
            }
            nasList = vNasRestRep.getDestinationVirtualNasIds();
            if (nasList != null) {
                if (this.destinationVirtualNasIds == null) {
                    this.destinationVirtualNasIds = new HashSet<String>();
                }
                for (URI nasUri : nasList) {
                    this.destinationVirtualNasIds.add(getViprClient().virtualNasServers().get(nasUri).getName());
                }
            }
        }
    }

    public static class VNasMoreInfo {
        private String storageObjects;
        private String storageCapacity;
        private String avgPercentagebusy;
        private String percentLoad;
        private String usedStorageCapacity;

        private String sourceVNASs;
        private String destinationVNASs;

        public VNasMoreInfo(VirtualNASRestRep vNasRestRep) {
            this.storageObjects = vNasRestRep.getStorageObjects();
            this.storageCapacity = vNasRestRep.getUsedStorageCapacity();
            this.avgPercentagebusy = vNasRestRep.getAvgPercentagebusy();
            this.percentLoad = vNasRestRep.getPercentLoad();
            this.usedStorageCapacity = vNasRestRep.getUsedStorageCapacity();
            // Setting Source and destination NAS names
            List<URI> nasList = vNasRestRep.getSourceVirtualNasIds();
            if (nasList != null) {
                if (this.sourceVNASs == null) {
                    this.sourceVNASs = "";
                }
                List<String> nasStringList = new ArrayList<String>();
                for (URI nasUri : nasList) {
                    VirtualNASRestRep nasServer = getViprClient().virtualNasServers().get(nasUri);
                    nasStringList.add(nasServer.getName() + "-"
                            + getViprClient().storageSystems().get(nasServer.getStorageDeviceURI()).getName());
                }
                sourceVNASs = StringUtils.join(nasStringList, ", ");
            }
            nasList = vNasRestRep.getDestinationVirtualNasIds();
            if (nasList != null) {
                if (this.destinationVNASs == null) {
                    this.destinationVNASs = "";
                }
                List<String> nasStringList = new ArrayList<String>();
                for (URI nasUri : nasList) {
                    VirtualNASRestRep nasServer = getViprClient().virtualNasServers().get(nasUri);
                    nasStringList.add(nasServer.getName() + "-"
                            + getViprClient().storageSystems().get(nasServer.getStorageDeviceURI()).getName());
                }
                destinationVNASs = StringUtils.join(nasStringList, ", ");
            }
        }
    }
}