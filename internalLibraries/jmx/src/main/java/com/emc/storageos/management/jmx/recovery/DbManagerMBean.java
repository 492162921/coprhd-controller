/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.jmx.recovery;

import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;

import java.util.Map;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;

public interface DbManagerMBean {

    /**
     * Get a map from node ID to their state.
     * 
     * @return Map from node ID (e.g. vipr1, vipr2, etc.) to state, true means up, false means down.
     */
    @ManagedAttribute(description = "Get mapping from vipr node id to up/down status, where true means up")
    public Map<String, Boolean> getNodeStates();

    /**
     * Remove a node from cluster.
     * 
     * @param nodeId The ID of vipr node, e.g. vipr1, vipr2, etc.
     */
    @ManagedOperation(description = "Remove a node from cluster")
    public void removeNode(String nodeId);

    /**
     * Trigger node repair for specified keyspace
     * 
     * @return The ID of the repair session.
     */
    @ManagedOperation(description = "Trigger node repair for specified keyspace")
    public void startNodeRepair(boolean canResume, boolean crossVdc) throws Exception;

    /**
     * Get status of last repair, can be either running, failed, or succeeded.
     * 
     * @param forCurrentNodesOnly If true, this method will only return repairs for current node set.
     *            If false, all historical repairs for any node set can be returned.
     * @return The object describing the status. null if no repair started yet.
     */
    @ManagedOperation(description = "Get status of last repair, can be either running, failed, or succeeded")
    public DbRepairStatus getLastRepairStatus(boolean forCurrentNodesOnly);

    /**
     * Get status of last succeeded repair, the returned status, if any, is always succeeded.
     * 
     * @param forCurrentNodesOnly If true, this method will only return repairs for current node set.
     *            If false, all historical repairs for any node set can be returned.
     * @return The object describing the status. null if no succeeded repair yet.
     */
    @ManagedOperation(description = "Get status of last succeeded repair, the returned status, if any, is always succeeded")
    public DbRepairStatus getLastSucceededRepairStatus(boolean forCurrentNodesOnly);

    @ManagedOperation(description = "Adjust number of tokens for this node to expected value in this software version, if it's not done already.")
    public
            boolean adjustNumTokens() throws InterruptedException;
}
