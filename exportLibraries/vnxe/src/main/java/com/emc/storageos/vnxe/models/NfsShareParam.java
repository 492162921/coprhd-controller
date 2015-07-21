/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonSerialize;
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class NfsShareParam {
    private String description;
    private boolean isReadOnly;
    private NFSShareDefaultAccessEnum defaultAccess;
    private List<VNXeBase> noAccessHosts;
    private List<VNXeBase> readOnlyHosts;
    private List<VNXeBase> readWriteHosts;
    private List<VNXeBase> rootAccessHosts;
    
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean getIsReadOnly() {
        return isReadOnly;
    }

    public void setIsReadOnly(boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public NFSShareDefaultAccessEnum getDefaultAccess() {
        return defaultAccess;
    }

    public void setDefaultAccess(NFSShareDefaultAccessEnum defaultAccess) {
        this.defaultAccess = defaultAccess;
    }

    public List<VNXeBase> getNoAccessHosts() {
        return noAccessHosts;
    }

    public void setNoAccessHosts(List<VNXeBase> noAccessHosts) {
        this.noAccessHosts = noAccessHosts;
    }

    public List<VNXeBase> getReadOnlyHosts() {
        return readOnlyHosts;
    }

    public void setReadOnlyHosts(List<VNXeBase> readOnlyHosts) {
        this.readOnlyHosts = readOnlyHosts;
    }

    public List<VNXeBase> getReadWriteHosts() {
        return readWriteHosts;
    }

    public void setReadWriteHosts(List<VNXeBase> readWriteHosts) {
        this.readWriteHosts = readWriteHosts;
    }

    public List<VNXeBase> getRootAccessHosts() {
        return rootAccessHosts;
    }

    public void setRootAccessHosts(List<VNXeBase> rootAccessHosts) {
        this.rootAccessHosts = rootAccessHosts;
    }

    public static enum NFSShareDefaultAccessEnum {
        NONE,
        READONLY,
        READWRITE,
        ROOT;
    }

}
