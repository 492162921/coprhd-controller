/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupCreateParams;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.impl.TaskUtil;
import com.emc.vipr.client.impl.RestClient;

/**
 * Remote Replication Group resources.
 * <p>
 * Base URL: <tt>/block/remotereplicationgroups</tt>
 *
 * @see RemoteReplicationGroupRestRep
 */
public class RemoteReplicationGroups {

    private RestClient client;

    public RemoteReplicationGroups(RestClient client) {
        this.client = client;
    }

    public RemoteReplicationGroupRestRep getRemoteReplicationGroupsRestRep(String uuid) {
        return client.get(RemoteReplicationGroupRestRep.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL + "/" + uuid);
    }

    public RemoteReplicationGroupList listRemoteReplicationGroups() {
        return client.get(RemoteReplicationGroupList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL);
    }

    public RemoteReplicationGroupList listValidRemoteReplicationGroups() {
        // valid groups are: reachable, have source & target systems
        return client.get(RemoteReplicationGroupList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL + "/valid");
    }

    public RemoteReplicationPairList listRemoteReplicationPairs(String uuid) {
        return client.get(RemoteReplicationPairList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL + "/" + uuid + "/pairs");
    }

    public RemoteReplicationPairList listRemoteReplicationPairsNotInCg(String uuid) {
        return client.get(RemoteReplicationPairList.class,
                PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL + "/" + uuid + "/pairs-not-in-cg");
    }

    public TaskResourceRep createRemoteReplicationGroup(RemoteReplicationGroupCreateParams params) {
        UriBuilder uriBuilder = client.uriBuilder(PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL +
                "/create-group");
        TaskResourceRep task = client.postURI(TaskResourceRep.class, params, uriBuilder.build());
        task = TaskUtil.waitForTask(client, task, 0);
        return task;
    }

    public BlockConsistencyGroupList listConsistencyGroups(String groupId) {
            return client.get(BlockConsistencyGroupList.class,
                    PathConstants.BLOCK_REMOTE_REPLICATION_GROUP_URL + "/" + groupId + "/consistency-groups");
    }
}
