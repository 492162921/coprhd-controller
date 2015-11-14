/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.management.jmx.recovery.DbManagerMBean;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.YamlConfigurationLoader;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.service.StorageService;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedResource;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MBean implementation for all db management operations
 */
@ManagedResource(objectName = DbManagerOps.MBEAN_NAME, description = "DB Manager MBean")
public class DbManager implements DbManagerMBean {
    private static final Logger log = LoggerFactory.getLogger(DbManager.class);

    private static final int REPAIR_INITIAL_WAIT_FOR_DBSTART_MINUTES = 5;
    // repair every 24*5 hours by default, given we do a proactive repair on start
    // once per five days on demand should suffice
    private static final int DEFAULT_DB_REPAIR_FREQ_MIN = 60 * 24 * 5;
    private int repairFreqMin = DEFAULT_DB_REPAIR_FREQ_MIN;

    @Autowired
    private CoordinatorClient coordinator;

    @Autowired
    private SchemaUtil schemaUtil;

    ScheduledFuture<?> scheduledRepairTrigger;

    // Max retry times after a db repair failure
    private int repairRetryTimes = 5;
    private ScheduledExecutorService executor = new NamedScheduledThreadPoolExecutor("DbRepairPool", 2);

    /**
     * Regular repair frequency in minutes
     * 
     * @param repairFreqMin
     */
    public void setRepairFreqMin(int repairFreqMin) {
        this.repairFreqMin = repairFreqMin;
    }

    /**
     * Start a node repair
     * 
     * @param keySpaceName
     * @param maxRetryTimes
     * @param crossVdc
     * @param noNewReapir
     * @return
     * @throws Exception
     */
    private boolean startNodeRepair(String keySpaceName, int maxRetryTimes, boolean crossVdc, boolean noNewReapir) throws Exception {
        DbRepairRunnable runnable = new DbRepairRunnable(this.executor, this.coordinator, keySpaceName,
                this.schemaUtil.isGeoDbsvc(), maxRetryTimes, crossVdc, noNewReapir);
        // call preConfig() here to set IN_PROGRESS for db repair triggered by schedule since we use it in getDbRepairStatus.
        runnable.preConfig();
        synchronized (runnable) {
            this.executor.submit(runnable);
            runnable.wait();
        }

        switch (runnable.getStatus()) {
            case ALREADY_RUNNING:
                return true;
            case NOT_THE_TIME:
                return false;
            case NOTHING_TO_RESUME:
                return false;
        }
        return true;
    }

    private static void addAll(Map<String, Boolean> stateMap, List<String> ips, boolean up) {
        for (String ip : ips) {
            stateMap.put(normalizeInetAddress(ip), up);
        }
    }

    private static String normalizeInetAddress(String ipString) {
        try {
            final DualInetAddress dualAddr = DualInetAddress.fromAddress(ipString);
            return dualAddr.hasInet4() ? dualAddr.getInet4() : dualAddr.getInet6();
        } catch (Exception e) {
            log.error("Failed to normalize ipaddr: {}", ipString, e);
            return null;
        }
    }

    @Override
    public Map<String, Boolean> getNodeStates() {
        Map<String, Boolean> ipStateMap = new TreeMap<>();
        while (true) {
            List<String> upNodes = StorageService.instance.getLiveNodes();
            List<String> downNodes = StorageService.instance.getUnreachableNodes();
            List<String> upNodes2 = StorageService.instance.getLiveNodes();
            if (new HashSet<>(upNodes).equals(new HashSet<>(upNodes2))) {
                addAll(ipStateMap, upNodes, true);
                addAll(ipStateMap, downNodes, false);
                break;
            }
        }

        Map<String, DualInetAddress> idIpMap = this.coordinator.getInetAddessLookupMap().getControllerNodeIPLookupMap();
        Map<String, Boolean> idStateMap = new TreeMap<>();
        for (Map.Entry<String, DualInetAddress> entry : idIpMap.entrySet()) {
            DualInetAddress dualAddr = entry.getValue();
            Boolean state = dualAddr.hasInet4() ? ipStateMap.get(dualAddr.getInet4()) : null;
            if (state == null) {
                state = dualAddr.hasInet6() ? ipStateMap.get(dualAddr.getInet6()) : null;
            }
            if (state != null) {
                idStateMap.put(entry.getKey(), state);
            }
        }

        return idStateMap;
    }

    @Override
    public void removeNode(String nodeId) {
        Map<String, DualInetAddress> idMap = this.coordinator.getInetAddessLookupMap().getControllerNodeIPLookupMap();
        DualInetAddress dualAddr = idMap.get(nodeId);
        if (dualAddr == null) {
            String errMsg = String.format("Cannot find node with name %s", nodeId);
            log.error(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        Map<String, String> hostIdMap = StorageService.instance.getHostIdMap();
        Map<String, String> ipGuidMap = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : hostIdMap.entrySet()) {
            ipGuidMap.put(normalizeInetAddress(entry.getKey()), entry.getValue());
        }

        String nodeGuid = dualAddr.hasInet4() ? ipGuidMap.get(dualAddr.getInet4()) : null;
        if (nodeGuid == null) {
            nodeGuid = dualAddr.hasInet6() ? ipGuidMap.get(dualAddr.getInet6()) : null;
        }
        if (nodeGuid == null) {
            String errMsg = String.format("Cannot find Cassandra node with IP address %s", dualAddr.toString());
            log.error(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        log.info("Removing Cassandra node {} on vipr node {}", nodeGuid, nodeId);
        StorageService.instance.removeNode(nodeGuid);
    }

    @Override
    public void startNodeRepair(boolean canResume, boolean crossVdc) throws Exception {
        // The return value is ignored as we are setting interval time to 0, it cannot be NotTheTime. And both AlreadyRunning and Started
        // are considered success. Though the already running repair may not for current cluster state, but that's same if it is and the
        // cluster state changed immediately after that.
        startNodeRepair(this.schemaUtil.getKeyspaceName(), canResume ? this.repairRetryTimes : 0, crossVdc, false);
    }

    private static DbRepairStatus getLastRepairStatus(DbRepairJobState state, String clusterDigest, int maxRetryTime) {
        if (state.getCurrentDigest() != null && (clusterDigest == null || clusterDigest.equals(state.getCurrentDigest()))) {
            if (state.getCurrentRetry() <= maxRetryTime) {
                return new DbRepairStatus(DbRepairStatus.Status.IN_PROGRESS,
                        new Date(state.getCurrentStartTime()), null, state.getCurrentProgress());
            } else {
                return new DbRepairStatus(DbRepairStatus.Status.FAILED,
                        new Date(state.getCurrentStartTime()), new Date(state.getCurrentUpdateTime()),
                        state.getCurrentProgress());
            }
        }

        return getLastSucceededRepairStatus(state, clusterDigest);
    }

    private static DbRepairStatus getLastSucceededRepairStatus(DbRepairJobState state, String clusterDigest) {
        if (state.getLastSuccessDigest() != null && (clusterDigest == null || clusterDigest.equals(state.getLastSuccessDigest()))) {
            return new DbRepairStatus(DbRepairStatus.Status.SUCCESS,
                    new Date(state.getLastSuccessStartTime()), new Date(state.getLastSuccessEndTime()), 100);
        }

        return null;
    }

    @Override
    public DbRepairStatus getLastRepairStatus(boolean forCurrentNodesOnly) {
        try {
            DbRepairJobState state = DbRepairRunnable.queryRepairState(this.coordinator, this.schemaUtil.getKeyspaceName(),
                    this.schemaUtil.isGeoDbsvc());

            DbRepairStatus retState = getLastRepairStatus(state, forCurrentNodesOnly ? DbRepairRunnable.getClusterStateDigest() : null,
                    this.repairRetryTimes);

            if (retState != null && retState.getStatus() == DbRepairStatus.Status.IN_PROGRESS) {
                // See if current state holder is still active, if not, we need to resume it
                String lockName = DbRepairRunnable.getLockName();
                InterProcessLock lock = coordinator.getLock(lockName);

                String currentHolder = DbRepairRunnable.getSelfLockNodeId(lock);
                if (currentHolder == null) { // No thread is actually driving the repair, we need to resume it
                    if (startNodeRepair(this.schemaUtil.getKeyspaceName(), this.repairRetryTimes, false, true)) {
                        log.info("Successfully resumed a previously paused repair");
                    } else {
                        log.warn("Cannot resume a previously paused repair, it could be another thread resumed and finished it");
                    }
                }
            }

            return retState;
        } catch (Exception e) {
            log.error("Failed to get node repair state from ZK", e);
            return null;
        }
    }

    @Override
    public DbRepairStatus getLastSucceededRepairStatus(boolean forCurrentNodesOnly) {
        try {
            DbRepairJobState state = DbRepairRunnable.queryRepairState(this.coordinator, this.schemaUtil.getKeyspaceName(),
                    this.schemaUtil.isGeoDbsvc());

            return getLastSucceededRepairStatus(state, forCurrentNodesOnly ? DbRepairRunnable.getClusterStateDigest() : null);
        } catch (Exception e) {
            log.error("Failed to get node repair state from ZK", e);
            return null;
        }
    }

    private Integer getNumTokensToSet() {
        int nodeCount = StorageService.instance.getLiveNodes().size();
        // no need to adjust token for single node
        if (nodeCount == 1) {
            return null;
        }

        // num_tokens is changed if current running value is different from what is in .yaml
        int numTokensEffective = StorageService.instance.getTokens().size();
        log.info("Current num_tokens running at: {}", numTokensEffective);

        int numTokensExpected;
        try {
            Config cfg = new YamlConfigurationLoader().loadConfig();
            numTokensExpected = cfg.num_tokens;
        } catch (ConfigurationException e) {
            log.error("Failed to load yaml file", e);
            return null;
        }

        return numTokensExpected != numTokensEffective ? numTokensExpected : null;
    }

    @Override
    public boolean adjustNumTokens() throws InterruptedException {
        Integer numTokensExpected = getNumTokensToSet();
        if (numTokensExpected != null) {
            log.info("Decommissioning DB...");
            try {
                StorageService.instance.decommission();
            } catch (Exception e) {
                log.error("Failed to decommission DB", e);
                throw e;
            }

            log.info("Successfully decommissioned db, enable auto bootstrap and set num_token override in ZK for this node");
            DbServiceImpl.instance.setConfigValue(DbConfigConstants.AUTOBOOT, Boolean.TRUE.toString());
            DbServiceImpl.instance.setConfigValue(DbConfigConstants.NUM_TOKENS_KEY, numTokensExpected.toString());
        }

        return numTokensExpected != null;
    }

    public void start() {
        this.scheduledRepairTrigger = this.executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    startNodeRepair(schemaUtil.getKeyspaceName(), repairRetryTimes, true, false);
                } catch (Exception e) {
                    log.error("Failed to trigger node repair", e);
                }
            }
        }, REPAIR_INITIAL_WAIT_FOR_DBSTART_MINUTES, repairFreqMin, TimeUnit.MINUTES);
    }

    @Override
    public boolean isDataCenterUnreachable(String dcName) {
        log.info("Check availability of data center {}", dcName);
        Set<InetAddress> liveNodes = Gossiper.instance.getLiveMembers();
        for (InetAddress nodeIp : liveNodes) {
            IEndpointSnitch snitch = DatabaseDescriptor.getEndpointSnitch();
            String dc = snitch.getDatacenter(nodeIp);
            log.info("node Ip {}, dcName {} ", nodeIp, dc);
            if (dc.equals(dcName)) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public void removeDataCenter(String dcName) {
        log.info("Remove Cassandra data center {}", dcName);
        Set<InetAddress> liveNodes = Gossiper.instance.getLiveMembers();
        Set<InetAddress> unreachableNodes = Gossiper.instance.getUnreachableMembers();
        List<InetAddress> allNodes = new ArrayList<InetAddress>();
        allNodes.addAll(liveNodes);
        allNodes.addAll(unreachableNodes);
        for (InetAddress nodeIp : allNodes) {
            IEndpointSnitch snitch = DatabaseDescriptor.getEndpointSnitch();
            String dc = snitch.getDatacenter(nodeIp);
            log.info("node Ip {}, dcName {} ", nodeIp, dc);
            if (dc.equals(dcName)) {
                Map<String, String> hostIdMap = StorageService.instance.getHostIdMap();
                String guid = hostIdMap.get(nodeIp.getHostAddress());
                log.info("Removing Cassandra node {} on vipr node {}", guid, nodeIp);
                Gossiper.instance.convict(nodeIp, 0);
                StorageService.instance.removeNode(guid);
            }
        }
    }
}
