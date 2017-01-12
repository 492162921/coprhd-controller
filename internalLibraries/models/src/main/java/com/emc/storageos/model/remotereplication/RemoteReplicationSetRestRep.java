package com.emc.storageos.model.remotereplication;

import com.emc.storageos.model.DataObjectRestRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashSet;
import java.util.Set;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "remote_replication_set")
public class RemoteReplicationSetRestRep extends DataObjectRestRep {

    // native id of replication set.
    private String nativeId;

    // Device label of this replication set
    private String deviceLabel;

    // If replication set is reachable.
    private Boolean reachable;

    // Type of storage systems in this replication set.
    private String storageSystemType;

    // Supported element types in this set: group/pair
    private Set<String> supportedElementTypes;

    // Supported remote replication modes
    private Set<String> supportedReplicationModes;

    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    @XmlElement(name = "name")
    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    @XmlElement(name = "reachable")
    public Boolean getReachable() {
        return reachable;
    }

    public void setReachable(Boolean reachable) {
        this.reachable = reachable;
    }


    @XmlElement(name = "storage_system_type")
    public String getStorageSystemType() {
        return storageSystemType;
    }

    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
    }

    @XmlElementWrapper(name = "supported_element_types")
     @XmlElement(name = "supported_element_type")
     public Set<String> getSupportedElementTypes() {
        if (supportedElementTypes == null) {
            supportedElementTypes = new HashSet<>();
        }
        return supportedElementTypes;
    }

    public void setSupportedElementTypes(Set<String> supportedElementTypes) {
        this.supportedElementTypes = supportedElementTypes;
    }

    @XmlElementWrapper(name = "supported_replication_modes")
    @XmlElement(name = "supported_replication_mode")
    public Set<String> getSupportedReplicationModes() {
        if (supportedReplicationModes == null) {
            supportedReplicationModes = new HashSet<>();
        }
        return supportedReplicationModes;
    }

    public void setSupportedReplicationModes(Set<String> supportedReplicationModes) {
        this.supportedReplicationModes = supportedReplicationModes;
    }
}
