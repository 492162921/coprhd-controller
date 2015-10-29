package com.emc.storageos.storagedriver.model;

import com.emc.storageos.storagedriver.storagecapabilities.CommonStorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.CustomStorageCapabilities;

public class StorageObject {

    // Device native ID for this storage object. Type: input/output. If not supplied, should be set by driver.
    private String nativeId;

    // Device label of this object. Type: input/output. If not supplied, should be set by driver.
    private String displayName;

    // Access status. Should be set by driver.
    private AccessStatus accessStatus;

    public static enum AccessStatus {
        READ_ONLY,
        WRITE_ONLY,
        READ_WRITE,
        NOT_READY

    }

    // NativeID of Consistency group for this storage object. Type: input/output.
    private String consistencyGroup;

    // Type: Output. Driver should set these capabilities at discovery/create time.
    private CommonStorageCapabilities commonCapabilities;

    // Type: Output. Driver should set these capabilities at discovery/create time.
    private CustomStorageCapabilities customCapabilities;

    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public AccessStatus getAccessStatus() {
        return accessStatus;
    }

    public void setAccessStatus(AccessStatus accessStatus) {
        this.accessStatus = accessStatus;
    }

    public String getConsistencyGroup() {
        return consistencyGroup;
    }

    public void setConsistencyGroup(String consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }

    public CommonStorageCapabilities getCommonCapabilities() {
        return commonCapabilities;
    }

    public void setCommonCapabilities(CommonStorageCapabilities commonCapabilities) {
        this.commonCapabilities = commonCapabilities;
    }

    public CustomStorageCapabilities getCustomCapabilities() {
        return customCapabilities;
    }

    public void setCustomCapabilities(CustomStorageCapabilities customCapabilities) {
        this.customCapabilities = customCapabilities;
    }
}
