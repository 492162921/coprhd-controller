/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.modelclient.model;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Encrypt;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.PermissionsIndex;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSetMap;

/**
 * Representation for a ViPR VDC
 */
@SuppressWarnings("serial")
@Cf("VirtualDataCenter")
public class VirtualDataCenter extends DataObject {
    private static final Logger log = LoggerFactory.getLogger(VirtualDataCenter.class);

    public static enum ConnectionStatus {
        ISOLATED,           // This Vdc is single site, not connected with others for a Geo system
        CONNECTING,         // In the process of connecting
        CONNECTING_SYNCED,  // In the process of connecting, vdc config synced to all sites
        CONNECTED,          // Connected in a Geo system
        CONNECT_PRECHECK_FAILED,    // Failed to connect to a Geo system during precheck
        CONNECT_FAILED,     // Failed to connect to a Geo system
        DISCONNECTING,      // In the process of disconnecting
        DISCONNECTED,       // Disconnected from a Geo system
        REMOVING,           // In the process of removing
        REMOVE_SYNCED,      // In the process of remove, vdc config synced to all sites
        REMOVE_PRECHECK_FAILED,     // Failed to remove from a geo system during precheck
        REMOVE_FAILED,      // Failed to remove from a geo system
        UPDATING,           // In the process of updating
        UPDATE_PRECHECK_FAILED,     // Failed to update vdc info during precheck
        UPDATE_FAILED,      // Failed to update vdc info
    }

    public static enum GeoReplicationStatus {
        REP_NONE,       // This vdc is isolated, no geo replication
        REP_PAUSED,     // This vdc is temporarily disconnected, geo replication paused
        REP_ERROR,      // Error occurred in geo replication, replication break
        REP_ALL,        // Geo replication is in health status
    }

    /**
     * Connection status of this VDC
     */
    private ConnectionStatus connectionStatus;

    /**
     * The status of geo replication
     */
    private GeoReplicationStatus repStatus;

    /**
     * Controller endpoint of for this VDC
     */
    private String apiEndpoint;

    /**
     * HMAC signing key for this VDC
     */
    private String secretKey;

    /**
     * new HMAC signing key for this VDC
     */
    private String securityNewKey;

    /**
     * Optional description of the VDC
     */
    private String description;

    /**
     * Certificate chain of the VDC
     */
    private String certificate_chain;

    /**
     * Internal version for this VDC object, used for change synchronization
     */
    private Long version;

    /**
     * Number of hosts in this VDC
     */
    private Integer hostCount;

    /**
     * Map of IPv4 adresses in the VDC
     * <nodeId, IPv4Address>
     */
    private StringMap hostIPv4AddressMap = new StringMap();

    /**
     * Map of IPv6 addresses in the VDC
     * <nodeId, IPv6Address>
     */
    private StringMap hostIPv6AddressMap = new StringMap();

    /**
     * Is this this my local VDC or a remote one
     */
    private Boolean local;

    /**
     * Short id for vdc like "vdc1", "vdc2"
     */
    private String shortId;

    private StringSetMap _roleAssignments;

    /**
     * Command endpoint using by data service for geo replication support
     */
    private String geoCommandEndpoint;

    /**
     * Data endpoint using by data service for geo replication support
     */
    private String geoDataEndpoint;

    @Name("apiEndpoint")
    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
        setChanged("apiEndpoint");
    }

    @Encrypt
    @Name("secretKey")
    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
        setChanged("secretKey");
    }

    @Encrypt
    @Name("securityNewKey")
    public String getSecurityNewKey() {
        return securityNewKey;
    }

    public void setSecurityNewKey(String securityNewKey) {
        this.securityNewKey = securityNewKey;
        setChanged("securityNewKey");
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged("description");
    }

    @Name("certificate_chain")
    public String getCertificateChain() {
        return certificate_chain;
    }

    public void setCertificateChain(String certificate_chain) {
        this.certificate_chain = certificate_chain;
        setChanged("certificate_chain");
    }

    @Name("connectionStatus")
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(ConnectionStatus connectionStatus) {
        this.connectionStatus = connectionStatus;
        setChanged("connectionStatus");
    }

    @Name("repStatus")
    public GeoReplicationStatus getRepStatus() {
        return repStatus;
    }

    public void setRepStatus(GeoReplicationStatus repStatus) {
        this.repStatus = repStatus;
        setChanged("repStatus");
    }

    @Name("version")
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
        setChanged("version");
    }

    @Name("hostCount")
    public Integer getHostCount() {
        return hostCount;
    }

    public void setHostCount(Integer hostCount) {
        this.hostCount = hostCount;
        setChanged("hostCount");
    }

    @Name("hostIPv4AddressesMap")
    public StringMap getHostIPv4AddressesMap() {
        return hostIPv4AddressMap;
    }

    public void setHostIPv4AddressesMap(StringMap hostIPv4AddressesMap) {
        this.hostIPv4AddressMap = hostIPv4AddressesMap;
        setChanged("hostIPv4AddressesMap");
    }

    @Name("hostIPv6AddressesMap")
    public StringMap getHostIPv6AddressesMap() {
        return hostIPv6AddressMap;
    }

    public void setHostIPv6AddressesMap(StringMap hostIPv6AddressesMap) {
        this.hostIPv6AddressMap = hostIPv6AddressesMap;
        setChanged("hostIPv6AddressesMap");
    }

    public Map<String, String> queryHostIPAddressesMap() {
        if (!hostIPv4AddressMap.isEmpty()) {
            return hostIPv4AddressMap;
        }

        return hostIPv6AddressMap;
    }

    public String queryLoopBackAddress() {
        if (!hostIPv4AddressMap.isEmpty()) {
            return "127.0.0.1";
        }
        return "[::1]";
    }

    @Name("local")
    public Boolean getLocal() {
        return local;
    }

    public void setLocal(Boolean local) {
        this.local = local;
        setChanged("local");
    }

    @Name("shortId")
    @AlternateId("AltIdIndex")
    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
        setChanged("shortId");
    }

    @PermissionsIndex("PermissionsIndex")
    @Name("role-assignment")
    public StringSetMap getRoleAssignments() {
        if (_roleAssignments == null) {
            _roleAssignments = new StringSetMap();
        }
        return _roleAssignments;
    }

    public void setRoleAssignments(StringSetMap roleAssignments) {
        _roleAssignments = roleAssignments;
    }

    public Set<String> getRoleSet(String key) {
        if (null != _roleAssignments) {
            return _roleAssignments.get(key);
        }
        return null;
    }

    public void addRole(String key, String role) {
        if (_roleAssignments == null) {
            _roleAssignments = new StringSetMap();
        }
        _roleAssignments.put(key, role);
    }

    public void removeRole(String key, String role) {
        if (_roleAssignments != null) {
            _roleAssignments.remove(key, role);
        }
    }

    @Name("geoCommandEndpoint")
    public String getGeoCommandEndpoint() {
        return geoCommandEndpoint;
    }

    public void setGeoCommandEndpoint(String geoCommandEndpoint) {
        this.geoCommandEndpoint = geoCommandEndpoint;
        setChanged("geoCommandEndpoint");
    }

    @Name("geoDataEndpoint")
    public String getGeoDataEndpoint() {
        return geoDataEndpoint;
    }

    public void setGeoDataEndpoint(String geoDataEndpoint) {
        this.geoDataEndpoint = geoDataEndpoint;
        setChanged("geoDataEndpoint");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().getName());

        builder.append("\n\tid:");
        builder.append(getId());

        builder.append("\n\tlocal:");
        builder.append(getLocal());

        builder.append("\n\tshortId:");
        builder.append(getShortId());

        builder.append("\n\tIPv4AddressesMap:");
        builder.append(getHostIPv4AddressesMap());

        builder.append("\n\tIPv6AddressesMap:");
        builder.append(getHostIPv6AddressesMap());

        return builder.toString();
    }
}
