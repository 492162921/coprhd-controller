/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

@Cf("BlockMirror")
public class BlockMirror extends Volume {

    // Reference to the volume representing the SystemElement
    private NamedURI _source;
    // CIM reference to the CIM_StorageSynchronized instance
    private String _synchronizedInstance;
    // Synchronization state
    private String _syncState;
    // Synchronization type
    private String _syncType;

    public static String MIRROR_SYNC_TYPE = "6";

    @NamedRelationIndex(cf = "NamedRelation3", type = Volume.class)
    @Name("source")
    public NamedURI getSource() {
        return _source;
    }

    public void setSource(NamedURI source) {
        _source = source;
        setChanged("source");
    }

    @Name("synchronizedInstance")
    public String getSynchronizedInstance() {
        return _synchronizedInstance;
    }

    public void setSynchronizedInstance(String synchronizedInstance) {
        _synchronizedInstance = synchronizedInstance;
        setChanged("synchronizedInstance");
    }

    @Name("syncState")
    public String getSyncState() {
        return _syncState;
    }

    public void setSyncState(String syncState) {
        _syncState = syncState;
        setChanged("syncState");
    }

    @Name("syncType")
    public String getSyncType() {
        return _syncType;
    }

    public void setSyncType(String syncType) {
        _syncType = syncType;
        setChanged("syncType");
    }
}