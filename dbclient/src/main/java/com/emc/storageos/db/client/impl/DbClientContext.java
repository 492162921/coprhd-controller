/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.cassandra.service.StorageProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.exceptions.ConnectionException;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Cluster;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.Host;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.retry.RetryPolicy;

public class DbClientContext {

    private static final String CASSANDRA_HOST_STATE_DOWN = "DOWN";

    private static final Logger log = LoggerFactory.getLogger(DbClientContext.class);

    private static final int DEFAULT_MAX_CONNECTIONS = 64;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 14;
    private static final int DEFAULT_SVCLIST_POLL_INTERVAL_SEC = 5;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 5;
    private static final int DEFAULT_MAX_BLOCKED_THREADS = 500;
    private static final String DEFAULT_CN_POOL_NANE = "DbClientPool";
    private static final long DEFAULT_CONNECTION_POOL_MONITOR_INTERVAL = 1000;
    private static final int MAX_QUERY_RETRY = 5;
    private static final int QUERY_RETRY_SLEEP_MS = 1000;
    private static final String LOCAL_HOST = "localhost";
    private static final int DB_THRIFT_PORT = 9160;
    private static final int GEODB_THRIFT_PORT = 9260;
    private static final String KEYSPACE_NETWORK_TOPOLOGY_STRATEGY = "NetworkTopologyStrategy";
    private static final int DEFAULT_CONSISTENCY_LEVEL_CHECK_SEC = 30;

    public static final String LOCAL_CLUSTER_NAME = "StorageOS";
    public static final String LOCAL_KEYSPACE_NAME = "StorageOS";
    public static final String GEO_CLUSTER_NAME = "GeoStorageOS";
    public static final String GEO_KEYSPACE_NAME = "GeoStorageOS";
    public static final long MAX_SCHEMA_WAIT_MS = 60 * 1000 * 10; // 10 minutes
    public static final int SCHEMA_RETRY_SLEEP_MILLIS = 10 * 1000; // 10 seconds

    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private int maxConnectionsPerHost = DEFAULT_MAX_CONNECTIONS_PER_HOST;
    private int svcListPoolIntervalSec = DEFAULT_SVCLIST_POLL_INTERVAL_SEC;
    private long monitorIntervalSecs = DEFAULT_CONNECTION_POOL_MONITOR_INTERVAL;
    private RetryPolicy retryPolicy = new QueryRetryPolicy(MAX_QUERY_RETRY, QUERY_RETRY_SLEEP_MS);
    private String keyspaceName = LOCAL_KEYSPACE_NAME;
    private String clusterName = LOCAL_CLUSTER_NAME;

    private AstyanaxContext<Keyspace> keyspaceContext;
    private Keyspace keyspace;
    private AstyanaxContext<Cluster> clusterContext;
    private Cluster cluster;

    private boolean initDone = false;
    private String cipherSuite;
    private String keyStoreFile;
    private String trustStoreFile;
    private String trustStorePassword;
    private boolean isClientToNodeEncrypted;
    private ScheduledExecutorService exe = Executors.newScheduledThreadPool(1);

    // whether to retry once with LOCAL_QUORUM for write failure 
    private boolean retryFailedWriteWithLocalQuorum = false; 
    
    private static final int DB_NATIVE_TRANSPORT_PORT = 9042;
    private static final int GEODB_NATIVE_TRANSPORT_PORT = 9043;
    
    private com.datastax.driver.core.Cluster cassandraCluster;
    private Session cassandraSession;
    private Map<String, PreparedStatement> prepareStatementMap = new HashMap<String, PreparedStatement>();
    
    public void setCipherSuite(String cipherSuite) {
        this.cipherSuite = cipherSuite;
    }

    public String getKeyspaceName() {
        return keyspaceName;
    }

    public void setKeyspaceName(String keyspaceName) {
        this.keyspaceName = keyspaceName;
    }

    public void setClientToNodeEncrypted(boolean isClientToNodeEncrypted) {
        this.isClientToNodeEncrypted = isClientToNodeEncrypted;
    }

    public boolean isClientToNodeEncrypted() {
        return isClientToNodeEncrypted;
    }

    public Keyspace getKeyspace() {
        if (keyspaceContext == null) {
            throw new IllegalStateException();
        }
        return keyspace;
    }
    
    public com.datastax.driver.core.Cluster getCassandraCluster() {
        if (cassandraCluster == null) {
            initClusterContext();
        }
        return cassandraCluster;
    }

    public void setHosts(Collection<Host> hosts) {
        if (keyspaceContext == null) {
            throw new IllegalStateException();
        }
        keyspaceContext.getConnectionPool().setHosts(hosts);
    }

    public int getPort() {
        return keyspaceContext.getConnectionPoolConfiguration().getPort();
    }

    public boolean isRetryFailedWriteWithLocalQuorum() {
        return retryFailedWriteWithLocalQuorum;
    }

    public void setRetryFailedWriteWithLocalQuorum(boolean retryFailedWriteWithLocalQuorum) {
        this.retryFailedWriteWithLocalQuorum = retryFailedWriteWithLocalQuorum;
    }

    /**
     * Cluster name
     * 
     * @param clusterName
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public void setSvcListPoolIntervalSec(int svcListPoolIntervalSec) {
        this.svcListPoolIntervalSec = svcListPoolIntervalSec;
    }

    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    /**
     * Sets the monitoring interval for client connection pool stats
     * 
     * @param monitorIntervalSecs
     */
    public void setMonitorIntervalSecs(long monitorIntervalSecs) {
        this.monitorIntervalSecs = monitorIntervalSecs;
    }
    
    public long getMonitorIntervalSecs() {
        return monitorIntervalSecs;
    }
    
    public boolean isInitDone() {
        return initDone;
    }

    public void setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    public String getTrustStoreFile() {
        return trustStoreFile;
    }

    public String getKeyStoreFile() {
        return keyStoreFile;
    }

    public void setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public void init(final HostSupplierImpl hostSupplier) {
        String svcName = hostSupplier.getDbSvcName();
        log.info("Initializing hosts for {}", svcName);
        List<CassandraHost> hosts = hostSupplier.get();
        if ((hosts != null) && (hosts.isEmpty())) {
            throw new IllegalStateException(String.format("DbClientContext.init() : host list in hostsupplier for %s is empty", svcName));
        } else {
            int hostCount = hosts == null ? 0 : hosts.size();
            log.info(String.format("number of hosts in the hostsupplier for %s is %d", svcName, hostCount));
        }
        
        // Check and reset default write consistency level
        final DrUtil drUtil = new DrUtil(hostSupplier.getCoordinatorClient());
        if (drUtil.isMultivdc()) {
            setRetryFailedWriteWithLocalQuorum(false); // geodb in mutlivdc should be EACH_QUORUM always. Never retry for write failures
            log.info("Retry for failed write with LOCAL_QUORUM: {}", retryFailedWriteWithLocalQuorum);
        } else {
            setRetryFailedWriteWithLocalQuorum(true);
        }
        if (drUtil.isActiveSite() && !drUtil.isMultivdc()) {
            log.info("Schedule db consistency level monitor on DR active site");
            exe.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        checkAndResetConsistencyLevel(drUtil, hostSupplier.getDbSvcName());
                    } catch (Exception ex) {
                        log.warn("Encounter Unexpected exception during check consistency level. Retry in next run", ex);
                    }
                }
            }, 60, DEFAULT_CONSISTENCY_LEVEL_CHECK_SEC, TimeUnit.SECONDS);
        }
        // init java driver
        String[] contactPoints = new String[hosts.size()];
        for (int i = 0; i < hosts.size(); i++) {
            contactPoints[i] = hosts.get(i).getHost();
        }
        cassandraCluster = com.datastax.driver.core.Cluster
                .builder()
                .addContactPoints(contactPoints).withPort(getNativeTransportPort()).build();
        cassandraCluster.getConfiguration().getQueryOptions().setConsistencyLevel(com.datastax.driver.core.ConsistencyLevel.LOCAL_QUORUM);
        cassandraSession = cassandraCluster.connect("\"" + keyspaceName + "\"");
        
        initDone = true;
    }

    public class ViPRRetryPolicy implements com.datastax.driver.core.policies.RetryPolicy {
        private int maxRetry;
        private int sleepInMS;

        public ViPRRetryPolicy(int maxRetry, int sleepInMS) {
            this.maxRetry = maxRetry;
            this.sleepInMS = sleepInMS;
        }

        public RetryDecision onReadTimeout(Statement statement, com.datastax.driver.core.ConsistencyLevel cl, int requiredResponses, int receivedResponses, boolean dataRetrieved, int nbRetry) {
            log.warn("onReadTimeout statement={} retried={} maxRetry={}", statement, nbRetry, maxRetry);
            if (nbRetry == maxRetry)
                return RetryDecision.rethrow();

            delay();

            return RetryDecision.retry(cl);
        }

        private void delay() {
            try {
                Thread.sleep(sleepInMS);
            } catch (InterruptedException e) {
                //ignore
            }
        }

        public RetryDecision onWriteTimeout(Statement statement, com.datastax.driver.core.ConsistencyLevel cl, WriteType writeType, int requiredAcks, int receivedAcks, int nbRetry) {
            log.warn("write timeout statement={} retried={} maxRetry={}", statement, nbRetry, maxRetry);
            if (nbRetry == maxRetry)
                return RetryDecision.rethrow();

            delay();
            // If the batch log write failed, retry the operation as this might just be we were unlucky at picking candidates
            return RetryDecision.retry(cl);
        }

        public RetryDecision onUnavailable(Statement statement, com.datastax.driver.core.ConsistencyLevel cl, int requiredReplica, int aliveReplica, int nbRetry) {
            log.warn("onUnavailable statement={} retried={} maxRetry={}", statement, nbRetry, maxRetry);
            if (nbRetry == maxRetry) {
                return RetryDecision.rethrow();
            }

            delay();
            return RetryDecision.tryNextHost(cl);
        }

        public RetryDecision onRequestError(Statement statement, com.datastax.driver.core.ConsistencyLevel cl, DriverException e, int nbRetry) {
            log.warn("onRequestError statement={} retried={} maxRetry={}", statement, nbRetry, maxRetry);
            if (nbRetry == maxRetry) {
                return RetryDecision.rethrow();
            }

            delay();
            return RetryDecision.tryNextHost(cl);

        }

        public void init(com.datastax.driver.core.Cluster cluster) {
            // nothing to do
        }

        public void close() {
            // nothing to do
        }
    }

    private com.datastax.driver.core.Cluster initConnection(String[] contactPoints) {
        return com.datastax.driver.core.Cluster
                .builder()
                .addContactPoints(contactPoints).withPort(getNativeTransportPort())
                .withClusterName(clusterName)
                .withRetryPolicy(new ViPRRetryPolicy(10, 1000))
                .build();
    }

    public KeyspaceMetadata getKeyspaceMetaData() {
        if (cassandraCluster == null) {
            initClusterContext();
        }
        return cassandraCluster.getMetadata().getKeyspace("\"" + keyspaceName + "\"");
    }

    public void createCF(String cfName, int gcPeriod, String compactionStrategy, String schema, String primaryKey) {
        String createCF = String.format("CREATE TABLE \"%s\".\"%s\" (%s,PRIMARY KEY (%s)) WITH COMPACT STORAGE AND " +
                                "speculative_retry = 'NONE' AND "+
                                "compaction = { 'class' : '%s' }",
                        keyspaceName, cfName, schema, primaryKey, compactionStrategy);
        if (gcPeriod != 0) {
            createCF +=" AND gc_grace_seconds = ";
            createCF += gcPeriod;
        }
        createCF +=";";

        log.info("createCF={}", createCF);

        cassandraSession.execute(createCF);
    }

    public void updateTable(TableMetadata cfd, String compactionStrategy, int gcGrace) {
        if (compactionStrategy == null && gcGrace <=0) {
            throw new IllegalArgumentException("compactionStrategy should not be null or gcGrace should >0");
        }

        String updateTable= String.format("ALTER TABLE \"%s\".\"%s\" with ", keyspaceName, cfd.getName());
        StringBuilder builder = new StringBuilder(updateTable);

        if (compactionStrategy != null) {
            builder.append("compaction = { 'class' : '");
            builder.append(compactionStrategy);
            builder.append("' }");
        }

        if (gcGrace > 0) {
            if (compactionStrategy != null) {
                builder.append(" AND ");
            }else {
                builder.append(" gc_grace_seconds=");
                builder.append(gcGrace);
            }
        }

        builder.append(";");

        String alterStatement = builder.toString();
        log.info("alter statement={}", alterStatement);

        cassandraSession.execute(alterStatement);
    }

    /**
     * Initialize the cluster context and cluster instances.
     * This has to be separated from init() because dbsvc need this to start
     * while init() depends on dbclient which in turn depends on dbsvc.
     */
    private void initClusterContext() {
        String[] contactPoints = {LOCAL_HOST};
        cassandraCluster = initConnection(contactPoints);
        String keyspaceString = String.format("\"%s\"", keyspaceName);
        if (cassandraCluster.getMetadata().getKeyspace(keyspaceString) == null) {
            cassandraSession = cassandraCluster.connect();
        } else {
            cassandraSession = cassandraCluster.connect(keyspaceString);
        }
        prepareStatementMap = new HashMap<String, PreparedStatement>();
    }

    /**
     * Check if it is geodbsvc
     *
     * @return
     */
    public boolean isGeoDbsvc() {
        return getKeyspaceName().equals(GEO_KEYSPACE_NAME);
    }

    public synchronized void stop() {
        if (keyspaceContext == null) {
            throw new IllegalStateException();
        }

        keyspaceContext.shutdown();
        keyspaceContext = null;

        if (clusterContext != null) {
            clusterContext.shutdown();
            clusterContext = null;
        }
        
        if (cassandraSession != null) {
            cassandraSession.close();
        }

        if (cassandraCluster != null) {
            cassandraCluster.close();
        }

        exe.shutdownNow();
    }

    /**
     * Update the strategy options for db or geodb service, depending on the content of this context instance.
     *
     * @param strategyOptions new strategy options to be updated
     * @param wait whether need to wait until schema agreement is reached.
     * @throws Exception
     */
    public void setCassandraStrategyOptions(Map<String, String> strategyOptions, boolean wait) {
        try {
            KeyspaceMetadata keyspaceMetadata = getKeyspaceMetaData();
            
            // ensure a schema agreement before updating the strategy options
            // or else it's destined to fail due to SchemaDisagreementException
            boolean hasUnreachableNodes = ensureSchemaAgreement();

            if (keyspaceMetadata != null) {
                updateKeySpace(strategyOptions, "alter");
            } else {
                updateKeySpace(strategyOptions, "create");
                if (wait && !hasUnreachableNodes) {
                    waitForSchemaAgreement();
                }
                return;
            }
    
            if (wait && !hasUnreachableNodes) {
                waitForSchemaAgreement();
            }
        } catch (ConnectionException ex) {
            log.error("Fail to update strategy option", ex);
            throw DatabaseException.fatals.failedToChangeStrategyOption(ex.getMessage());
        }
    }

    private void updateKeySpace(Map<String, String> strategyOptions, String action) {
        StringBuilder replications = new StringBuilder();
        boolean appendComma = false;

        for (Map.Entry<String, String> option : strategyOptions.entrySet()) {
            if (appendComma == false) {
                appendComma = true;
            }else {
                replications.append(",");
            }

            replications.append("'")
                    .append(option.getKey())
                    .append("' : ")
                    .append(option.getValue());
        }

        String createKeySpace=String.format("%s KEYSPACE \"%s\" WITH replication = { 'class': '%s', %s };",
                action, keyspaceName, KEYSPACE_NETWORK_TOPOLOGY_STRATEGY, replications.toString());
        log.info("update keyspace using the cql statement:{}", createKeySpace);
        cassandraSession.execute(createKeySpace);
    }

    public void waitForSchemaAgreement() {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < MAX_SCHEMA_WAIT_MS) {
            if (cassandraCluster.getMetadata().checkSchemaAgreement()) {
                log.info("schema agreement achieved");
                return;
            }

            log.info("waiting for schema change ...");
            try {
                Thread.sleep(SCHEMA_RETRY_SLEEP_MILLIS);
            } catch (InterruptedException ex) {}
        }

        log.warn("Unable to achieve schema agressment");
   }

    /**
     * Remove a specific dc from strategy options, and wait till the new schema reaches all sites.
     * If the dc doesn't exist in the current strategy options, nothing changes.
     *
     * @param dcId the dc to be removed
     * @throws Exception
     */
    public void removeDcFromStrategyOptions(String dcId)  {
        Map<String, String> strategyOptions;
        try {
            strategyOptions = getStrategyOptions();
        } catch (DriverException ex) {
            log.error("Unexpected errors to describe keyspace", ex);
            throw DatabaseException.fatals.failedToChangeStrategyOption(ex.getMessage());
        }
        if (strategyOptions.containsKey(dcId)) {
            log.info("Remove dc {} from strategy options", dcId);
            strategyOptions.remove(dcId);

            setCassandraStrategyOptions(strategyOptions, true);
        }
    }

    /**
     * Try to reach a schema agreement among all the reachable nodes
     *
     * @return true if there are unreachable nodes
     */
    public boolean ensureSchemaAgreement() {
        long start = System.currentTimeMillis();
        Map<String, List<String>> schemas = null;
        while (System.currentTimeMillis() - start < DbClientContext.MAX_SCHEMA_WAIT_MS) {
            try {
                log.info("sleep for {} seconds before checking schema versions.",
                        DbClientContext.SCHEMA_RETRY_SLEEP_MILLIS / 1000);
                Thread.sleep(DbClientContext.SCHEMA_RETRY_SLEEP_MILLIS);
            } catch (InterruptedException ex) {
                log.warn("Interrupted during sleep");
            }

            schemas = getSchemaVersions();
            if (schemas.size() > 2) {
                // there are more than two schema versions besides UNREACHABLE, keep waiting.
                continue;
            }
            if (schemas.size() == 1) {
                if (!schemas.containsKey(StorageProxy.UNREACHABLE)) {
                    return false;
                } else {
                    // keep waiting if all nodes are unreachable
                    continue;
                }
            }
            // schema.size() == 2, if one of them is UNREACHABLE, return
            if (schemas.containsKey(StorageProxy.UNREACHABLE)) {
                return true;
            }
            // else continue waiting
        }
        log.error("Unable to converge schema versions {}", schemas);
        throw new IllegalStateException("Unable to converge schema versions");
    }

    /**
     * Get Cassandra schema versions -> nodes mapping.
     *
     * @return
     */
    public Map<String, List<String>> getSchemaVersions() {
        Map<String, List<String>> versions = new HashMap<String, List<String>>();
        try {
            Set<com.datastax.driver.core.Host> allHostSet = cassandraCluster.getMetadata().getAllHosts();
            Iterator<com.datastax.driver.core.Host> allHosts = allHostSet.iterator();
            
            while (allHosts.hasNext()){
                com.datastax.driver.core.Host host = allHosts.next();
                if (host.getState().equals(CASSANDRA_HOST_STATE_DOWN)) {
                    allHosts.remove();
                    versions.putIfAbsent(StorageProxy.UNREACHABLE, new LinkedList<String>());
                    versions.get(StorageProxy.UNREACHABLE).add(host.getBroadcastAddress().getHostAddress());
                }
            }

            ResultSet result = cassandraSession.execute("select * from system.peers");
            for (Row row : result) {
                versions.putIfAbsent(row.getUUID("schema_version").toString(), new LinkedList<String>());
                versions.get(row.getUUID("schema_version").toString()).add(row.getInet("rpc_address").getHostAddress());
            }
            
            result = cassandraSession.execute("select * from system.local");
            Row localNode = result.one();
            versions.putIfAbsent(localNode.getUUID("schema_version").toString(), new LinkedList<String>());
            versions.get(localNode.getUUID("schema_version").toString()).add(localNode.getInet("broadcast_address").getHostAddress());
            
        } catch (com.datastax.driver.core.exceptions.ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }

        log.info("schema versions found: {}", versions);
        return versions;
    }
    
    public Map<String, String> getStrategyOptions() {
        Map<String, String> result = new HashMap<String, String>();
        KeyspaceMetadata keyspaceMetadata = cassandraCluster.getMetadata().getKeyspace("\""+this.getKeyspaceName()+"\"");
        Map<String, String> replications = keyspaceMetadata.getReplication();
        for (Map.Entry<String, String> entry : replications.entrySet()) {
            if (!entry.getKey().startsWith("class")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }

    private void checkAndResetConsistencyLevel(DrUtil drUtil, String svcName) {
        
        if (isRetryFailedWriteWithLocalQuorum() && drUtil.isMultivdc()) {
            log.info("Disable retry for write failure in multiple vdc configuration");
            setRetryFailedWriteWithLocalQuorum(false);
            return;
        }
        
        ConsistencyLevel currentConsistencyLevel = getKeyspace().getConfig().getDefaultWriteConsistencyLevel();
        if (currentConsistencyLevel.equals(ConsistencyLevel.CL_EACH_QUORUM)) {
            log.debug("Write consistency level is EACH_QUORUM. No need adjust");
            return;
        }
        
        log.info("Db consistency level for {} is downgraded as LOCAL_QUORUM. Check if we need reset it back", svcName);
        for(Site site : drUtil.listStandbySites()) {
            if (site.getState().equals(SiteState.STANDBY_PAUSED) ||
                    site.getState().equals(SiteState.STANDBY_DEGRADED)) {
                continue; // ignore a standby site which is paused by customer explicitly
            }
            String siteUuid = site.getUuid();
            int count = drUtil.getNumberOfLiveServices(siteUuid, svcName);
            if (count <= site.getNodeCount() / 2) {
                log.info("Service {} of quorum nodes on site {} is down. Still keep write consistency level to LOCAL_QUORUM", svcName, siteUuid);
                return;
            }      
        }
        log.info("Service {} of quorum nodes on all standby sites are up. Reset default write consistency level back to EACH_QUORUM", svcName);
        AstyanaxConfigurationImpl config = (AstyanaxConfigurationImpl)keyspaceContext.getAstyanaxConfiguration();
        config.setDefaultWriteConsistencyLevel(ConsistencyLevel.CL_EACH_QUORUM);
    }
    
    protected int getNativeTransportPort() {
        int port = isGeoDbsvc() ? GEODB_NATIVE_TRANSPORT_PORT : DB_NATIVE_TRANSPORT_PORT;
        return port;
    }

    public Session getSession() {
        if (cassandraCluster == null || cassandraCluster.isClosed()) {
            this.initClusterContext();
        }
        return cassandraSession;
    }
    
    public PreparedStatement getPreparedStatement(String queryString) {
        if (!prepareStatementMap.containsKey(queryString)) {
            PreparedStatement statement = getSession().prepare(queryString);
            prepareStatementMap.put(queryString, statement);
        }
        return prepareStatementMap.get(queryString);
    }
}
