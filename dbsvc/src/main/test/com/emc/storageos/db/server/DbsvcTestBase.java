/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server;

import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.db.TestDBClientUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.common.DbServiceStatusChecker;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.server.impl.DbServiceImpl;
import com.emc.storageos.db.server.impl.MigrationHandlerImpl;
import com.emc.storageos.db.server.impl.SchemaUtil;
import com.emc.storageos.db.server.util.StubBeaconImpl;
import com.emc.storageos.db.server.util.StubCoordinatorClientImpl;
import com.emc.storageos.security.geo.GeoDependencyChecker;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.services.util.JmxServerWrapper;
import com.emc.storageos.services.util.LoggingUtils;
import org.apache.cassandra.config.Schema;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * Dbsvc unit test base
 */
public class DbsvcTestBase {
    static {
        LoggingUtils.configureIfNecessary("dbtest-log4j.properties");
    }

    private static final Logger _log = LoggerFactory.getLogger(DbsvcTestBase.class);
    protected static DbServiceImpl _dbsvc;
    protected static ServiceImpl service;
    protected static DbClientImpl _dbClient;
    protected static boolean isDbStarted = false;
    protected static DbVersionInfo _dbVersionInfo;
    protected static File _dataDir;
    protected static CoordinatorClient _coordinator = new StubCoordinatorClientImpl(
            URI.create("thrift://localhost:9160"));
    protected static EncryptionProviderImpl _encryptionProvider = new EncryptionProviderImpl();
    protected static Map<String, List<BaseCustomMigrationCallback>> customMigrationCallbacks
            = new HashMap<>();
    protected static DbServiceStatusChecker statusChecker = null;
    protected static GeoDependencyChecker _geoDependencyChecker;

    // This controls whether the JMX server is started with DBSVC or not. JMX server is used to snapshot Cassandra
    // DB files and dump SSTables to JSON files. However, current JmxServerWrapper.start() implementation blindly
    // calls LocateRegistry.createRegistry() to start a new RMI Registry server on port e.g. 7199. In test cases
    // like DbUpgradeTest which stops the DBSVC then start it again in same OS process, the previous RMI Registry
    // server is still alive and listening on the port, so the 2nd call to LocateRegistry.createRegistry() will
    // throw an exception. There's no standard way to shutdown a RMI Registry server.
    protected static boolean _startJmx;

    /**
     * Deletes given directory
     *
     * @param dir
     */
    protected static void cleanDirectory(File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                cleanDirectory(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }
    
    
    @BeforeClass
    public static void setup() throws IOException {
        _dbVersionInfo = new DbVersionInfo();
        _dbVersionInfo.setSchemaVersion("2.2");
        _dataDir = new File("./dbtest");
        if (_dataDir.exists() && _dataDir.isDirectory()) {
            cleanDirectory(_dataDir);
        }
        startDb(_dbVersionInfo.getSchemaVersion(), null);
    }

    @AfterClass
    public static void stop() {
        if (isDbStarted)
            stopAll();

        if(_dataDir!=null){
            cleanDirectory(_dataDir);
            _dataDir=null;
        }

        _log.info("The Dbsvc is stopped");
    }


    protected static void stopAll() {
        TypeMap.clear();
        Introspector.flushCaches();

        isDbStarted = false;
        _dbsvc.stop();

        Schema.instance.clear();

        _log.info("The dbsvc is stopped");

    }

    /**
     * Start embedded DB
     */
    protected static void startDb(String schemaVersion, String extraModelsPkg) throws IOException {
        startDb(schemaVersion, extraModelsPkg, null);
    }

    /**
     * Start embedded DB
     */
    protected static void startDb(String schemaVersion, String extraModelsPkg, DataObjectScanner scanner) throws IOException {
        _dbVersionInfo = new DbVersionInfo();
        _dbVersionInfo.setSchemaVersion(schemaVersion);

        List<String> packages = new ArrayList<String>();
        packages.add("com.emc.storageos.db.client.model");
        packages.add("com.emc.sa.model");
        if (extraModelsPkg != null) {
            packages.add(extraModelsPkg);
        }
        String[] pkgsArray = packages.toArray(new String[packages.size()]);

        service = new ServiceImpl();
        service.setName("dbsvc");
        service.setVersion(schemaVersion);
        service.setEndpoint(URI.create("thrift://localhost:9160"));
        service.setId("db-standalone");

        StubBeaconImpl beacon = new StubBeaconImpl(service);
        if (scanner == null) {
            scanner = new DataObjectScanner();
            scanner.setPackages(pkgsArray);
            scanner.init();
        }

        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("nodeaddrmap-var.xml");

        CoordinatorClientInetAddressMap inetAddressMap = (CoordinatorClientInetAddressMap)ctx.getBean("inetAddessLookupMap");

        if (inetAddressMap == null) {
        	_log.error("CoordinatorClientInetAddressMap is not initialized. Node address lookup will fail.");
        }

        _coordinator.setInetAddessLookupMap(inetAddressMap);
        _coordinator.setDbVersionInfo(_dbVersionInfo);
        
        statusChecker = new DbServiceStatusChecker();
        statusChecker.setCoordinator(_coordinator);
        statusChecker.setClusterNodeCount(1);
        statusChecker.setDbVersionInfo(_dbVersionInfo);
        statusChecker.setServiceName(service.getName());

        SchemaUtil util = new MockSchemaUtil();
        util.setKeyspaceName("Test");
        util.setClusterName("Test");
        util.setDataObjectScanner(scanner);
        util.setService(service);
        util.setStatusChecker(statusChecker);
        util.setCoordinator(_coordinator);
        util.setVdcShortId("vdc1");

        DbClientContext dbctx = new DbClientContext();
        dbctx.setClusterName("Test");
        dbctx.setKeyspaceName("Test");
        util.setClientContext(dbctx);

        Properties props = new Properties();
        props.put(DbClientImpl.DB_STAT_OPTIMIZE_DISK_SPACE, "false");
        util.setDbCommonInfo(props);
        List<String> vdcHosts = new ArrayList();
        vdcHosts.add("127.0.0.1");
        util.setVdcNodeList(vdcHosts);
        util.setDbCommonInfo(new java.util.Properties()); 
        
        JmxServerWrapper jmx = new JmxServerWrapper();
        if (_startJmx) {
            jmx.setEnabled(true);
            jmx.setServiceUrl("service:jmx:rmi://localhost:%d/jndi/rmi://%s:%d/jmxrmi");
            jmx.setHost("127.0.0.1");
            jmx.setPort(7199);
            jmx.setExportPort(7200);
        } else {
            jmx.setEnabled(false);
        }

        _encryptionProvider.setCoordinator(_coordinator);
        
        _dbClient = getDbClientBase();
        PasswordUtils passwordUtils = new PasswordUtils();
        passwordUtils.setCoordinator(_coordinator);
        passwordUtils.setEncryptionProvider(_encryptionProvider);
        passwordUtils.setDbClient(_dbClient);
        util.setPasswordUtils(passwordUtils);
        
        MigrationHandlerImpl handler = new MigrationHandlerImpl();
        handler.setPackages(pkgsArray);
        handler.setService(service);
        handler.setStatusChecker(statusChecker);
        handler.setCoordinator(_coordinator);
        handler.setDbClient(_dbClient);
        handler.setSchemaUtil(util);
        handler.setPackages(pkgsArray);

        handler.setCustomMigrationCallbacks(customMigrationCallbacks);
        
        DependencyChecker localDependencyChecker = new DependencyChecker(_dbClient, scanner);
        _geoDependencyChecker = new GeoDependencyChecker(_dbClient, _coordinator, localDependencyChecker);
        
        _dbsvc = new DbServiceImpl();
        _dbsvc.setConfig("db-test.yaml");
        _dbsvc.setSchemaUtil(util);
        _dbsvc.setCoordinator(_coordinator);
        _dbsvc.setStatusChecker(statusChecker);
        _dbsvc.setService(service);
        _dbsvc.setJmxServerWrapper(jmx);
        _dbsvc.setDbClient(_dbClient);
        _dbsvc.setBeacon(beacon);
        _dbsvc.setMigrationHandler(handler);
        _dbsvc.setDisableScheduledDbRepair(true);
        _dbsvc.start();

        isDbStarted = true;
    }

    /**
     * Create DbClient to embedded DB
     *
     * @return
     */
    protected static DbClient getDbClient() {
        return getDbClient(new InternalDbClient());
    }

    protected static DbClient getDbClient(InternalDbClient dbClient) {
        dbClient = getDbClientBase(dbClient);
        dbClient.setBypassMigrationLock(false);
        dbClient.start();
        return dbClient;
    }

    protected static DbClientImpl getDbClientBase() {
        return getDbClientBase(new InternalDbClient());
    }

    protected static InternalDbClient getDbClientBase(InternalDbClient dbClient) {
        dbClient.setCoordinatorClient(_coordinator);
        dbClient.setDbVersionInfo(_dbVersionInfo);
        dbClient.setBypassMigrationLock(true);
        _encryptionProvider.setCoordinator(_coordinator);
        dbClient.setEncryptionProvider(_encryptionProvider);
        
        DbClientContext localCtx = new DbClientContext();
        localCtx.setClusterName("Test");
        localCtx.setKeyspaceName("Test");
        dbClient.setLocalContext(localCtx);
        
        VdcUtil.setDbClient(dbClient);

        return dbClient;
    }

    protected static CoordinatorClient getCoordinator(){
        return _coordinator;
    }

    static class MockSchemaUtil extends SchemaUtil {
        @Override
        public void insertVdcVersion(final DbClient dbClient) {
            //Do nothing
        }
    }
}
