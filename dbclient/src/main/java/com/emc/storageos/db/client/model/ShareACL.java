/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

public abstract class ShareACL extends DataObject {

    protected String user;
    protected String group;
    protected String domain;

    // Name of the cifs share
    protected String shareName;

    // Permissions for user or group: read(r), change (rw) or full control
    protected String permission;

    // deviceShareId is the uid of the export on the array. Currently Isilon uses it
    // NetApp and VNXFile don't use this field.
    protected String deviceSharePath;

    protected String fileSystemShareACLIndex;
    protected String snapshotShareACLIndex;

    public static enum SupportedPermissions {
        read, change, fullcontrol
    }

    @Name("user")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
        setChanged("user");
    }

    @Name("group")
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
        setChanged("group");
    }

    @Name("domain")
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
        setChanged("domain");
    }

    @Name("shareName")
    public String getShareName() {
        return shareName;
    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
        setChanged("shareName");
    }

    @Name("permission")
    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
        setChanged("permission");
    }

    @Name("deviceSharePath")
    public String getDeviceSharePath() {
        return deviceSharePath;
    }

    public void setDeviceSharePath(String deviceSharePath) {
        this.deviceSharePath = deviceSharePath;
        setChanged("deviceSharePath");
    }

    @Name("fileSystemShareACLIndex")
    @AlternateId("fileSystemShareACLIndexTable")
    public String getFileSystemShareACLIndex() {
        return fileSystemShareACLIndex;
    }

    public void setFileSystemShareACLIndex(String fileSystemShareACLIndex) {
        this.fileSystemShareACLIndex = fileSystemShareACLIndex;
        setChanged("fileSystemShareACLIndex");
    }

    @Name("snapshotShareACLIndex")
    @AlternateId("snapshotShareACLIndexTable")
    public String getSnapshotShareACLIndex() {
        return snapshotShareACLIndex;
    }

    public void setSnapshotShareACLIndex(String snapshotShareACLIndex) {
        this.snapshotShareACLIndex = snapshotShareACLIndex;
        setChanged("snapshotShareACLIndex");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ShareACL [user=");
        builder.append(user);
        builder.append(", group=");
        builder.append(group);
        builder.append(", shareName=");
        builder.append(shareName);
        builder.append(", permission=");
        builder.append(permission);
        builder.append(", deviceSharePath=");
        builder.append(deviceSharePath);
        builder.append("]");
        return builder.toString();
    }

    public abstract void calculateACLIndex();
}
