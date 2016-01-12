/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

public class IsilonSyncTargetPolicy extends IsilonSyncPolicy {

    public static enum FOFB_STATES {
        writes_disabled, enabling_writes, writes_enabled, disabling_writes, creating_resync_policy, resync_policy_created
    }

    private IsilonSyncTargetPolicy.FOFB_STATES failover_failback_state;
    private String source_host;

    public IsilonSyncTargetPolicy.FOFB_STATES getFoFbState() {
        return failover_failback_state;
    }

    public String getSource_host() {
        return source_host;
    }

    @Override
    public String toString() {
        return "IsilonSyncTargetPolicy [Policy_Name=" + getName() + ", Target_path=" + getTarget_path() + ", Last_job_state="
                + getLast_job_state() + ", failover_failback_state=" + failover_failback_state + ", source_host=" + source_host + "]";
    }

}
