/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model;

import com.emc.storageos.driver.univmax.smc.basetype.DefaultParameter;

public class CreateStorageGroupParameter extends DefaultParameter {
    private String storageGroupId;
    private Boolean create_empty_storage_group;
    private String srpId;
    private String emulation;
    private SloBasedStorageGroupParamType[] sloBasedStorageGroupParam;

    public CreateStorageGroupParameter(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    public CreateStorageGroupParameter setCreateEmptyStorageGroup(boolean createEmptyStorageGroup) {
        this.create_empty_storage_group = createEmptyStorageGroup;
        return this;
    }

    public CreateStorageGroupParameter setSrpId(String srpId) {
        this.srpId = srpId;
        return this;
    }

    public void setSloBasedStorageGroupParam(SloBasedStorageGroupParamType[] sloBasedStorageGroupParam) {
        this.sloBasedStorageGroupParam = sloBasedStorageGroupParam;
    }

    /**
     * @param emulation the emulation to set
     */
    public void setEmulation(String emulation) {
        this.emulation = emulation;
    }

    /**
     * @return the storageGroupId
     */
    public String getStorageGroupId() {
        return storageGroupId;
    }

}
