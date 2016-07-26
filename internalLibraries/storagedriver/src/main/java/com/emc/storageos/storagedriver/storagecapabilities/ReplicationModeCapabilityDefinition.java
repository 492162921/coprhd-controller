/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;


public class ReplicationModeCapabilityDefinition extends CapabilityDefinition {

    // The uid of this capability definition.
    public static final String CAPABILITY_UID = "replicationMode";

    // The names of the supported properties.
    public static enum PROPERTY_NAME {
        MODE_ID,
        GROUP_CONSISTENCY_ENFORCED_AUTOMATICALLY,
        GROUP_CONSISTENCY_NOT_SUPPORTED
    };

    // TODO add uncomment
    public ReplicationModeCapabilityDefinition() {
        // super(CAPABILITY_UID);
    }
}
