/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.beans.Transient;
import java.net.URI;

import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;

@Cf("ExportPathParams")
public class ExportPathParams extends DiscoveredDataObject {
    private Integer maxPaths;
    private Integer minPaths;
    private Integer pathsPerInitiator;
    private Integer maxInitiatorsPerPort;
    // storage ports to be used for port allocation if supplied
    private StringSet storagePorts;
    // Default exportGroupType is Host. Expressed in ExportGroup.ExportGroupType
    private String exportGroupType;
    // If explicitly created is true, a user specifically create an ExportPathParam record.
    // If explicitly created is false, the entry was created as a side effect of an export operation.
    private Boolean explicitlyCreated;
    // port group name predefined in the vmax
    private String portGroup;
    
    private URI storageDevice;
    
    /*
     * If allowFewerPorts is true, may allocate fewer than the calculated port requirement
     * for a Network. This is used for RP situations where we're zoning all Initiators to all Ports.
     */
    private Boolean allowFewerPorts = false;
    
    // Return the default params if asked.
    // MaxPaths will be set to 4.
    // Minpaths will be set to 0 which is ignored.
    // PathsPerInitiator is set to 0 which means determine by array type.
    // MaxInitiatorsPerPort will be set to 1 which means ports are not shared by Initiators.
    static public final ExportPathParams defaultParams = new ExportPathParams(4, 0, 0);

    static public ExportPathParams getDefaultParams() {
        return defaultParams;
    }
    
    public ExportPathParams() {
        // default constructor needed for persistence framework
    }

    public ExportPathParams(int maxPaths, int minPaths, int pathsPerInitiator) {
        this(maxPaths, minPaths, pathsPerInitiator, ExportGroupType.Host);
    }

    public ExportPathParams(int maxPaths, int minPaths, int pathsPerInitiator, ExportGroupType type) {
        this.maxPaths = maxPaths;
        this.minPaths = minPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.exportGroupType = type.toString();
        this.maxInitiatorsPerPort = 1;
    }
    
    public String toString() {
        return String.format("type %s maxPaths %d minPaths %d pathsPerInitiator %d maxInitiatorsPerPort %d",
                returnExportGroupType().name(), getMaxPaths(), getMinPaths(), getPathsPerInitiator(), getMaxInitiatorsPerPort());
    }
    
    @Name("maxPaths")
    public Integer getMaxPaths() {
        return maxPaths;
    }
    
    public void setMaxPaths(Integer maxPaths) {
        this.maxPaths = maxPaths;
        setChanged("maxPaths");
    }
    
    @Name("minPaths")
    public Integer getMinPaths() {
        return minPaths;
    }
    
    public void setMinPaths(Integer minPaths) {
        this.minPaths = minPaths;
        setChanged("minPaths");
    }
    
    @Name("pathsPerInitiator")
    public Integer getPathsPerInitiator() {
        return pathsPerInitiator;
    }
    
    public void setPathsPerInitiator(Integer pathsPerInitiator) {
        this.pathsPerInitiator = pathsPerInitiator;
        setChanged("pathsPerInitiator");
    }
    
    @Name("storagePorts")
    public StringSet getStoragePorts() {
        if (storagePorts == null) {
            return new StringSet();
        }
        return storagePorts;
    }
    
    public void setStoragePorts(StringSet storagePorts) {
        this.storagePorts = storagePorts;
        setChanged("storagePorts");
    }

    @Name("portGroup")
    public String getPortGroup() {
        return portGroup;
    }
    
    public void setPortGroup(String portGroup) {
        this.portGroup = portGroup;
        setChanged("portGroup");
    }
    
    @Name("explicitlyCreated")
    public Boolean getExplicitlyCreated() {
        return explicitlyCreated;
    }
    
    public boolean wasExplicitlyCreated() {
        return (explicitlyCreated != null && explicitlyCreated);
    }

    public void setExplicitlyCreated(Boolean explicitlyCreated) {
        this.explicitlyCreated = explicitlyCreated;
    }

    @Name("exportGroupType")
    public String getExportGroupType() {
                return exportGroupType;
    }
    
    public ExportGroupType returnExportGroupType() {
        if (getExportGroupType() == null) {
            return ExportGroupType.Host;
        }
        return ExportGroupType.valueOf(getExportGroupType());
    }

    public void setExportGroupType(String exportGroupType) {
                this.exportGroupType = exportGroupType;
    }

    @Transient
    public Boolean getAllowFewerPorts() {
                return allowFewerPorts;
    }

    public void setAllowFewerPorts(Boolean allowFewerPorts) {
        this.allowFewerPorts = allowFewerPorts;
    }

    @Transient
    public Integer getMaxInitiatorsPerPort() {
        if (maxInitiatorsPerPort == null) {
            // 1 is the default because we normally want only one initiator to use each storage port
            return 1;
        }
        return maxInitiatorsPerPort;
    }

    public void setMaxInitiatorsPerPort(Integer maxInitiatorsPerPort) {
        this.maxInitiatorsPerPort = maxInitiatorsPerPort;
    }
    
    @RelationIndex(cf = "ExportPathToStorageDevice", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        this.storageDevice = storageDevice;
        setChanged("storageDevice");
    }

}
