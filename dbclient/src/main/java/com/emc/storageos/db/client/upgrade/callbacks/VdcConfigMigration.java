/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * Cleanup db config from pre-yoda release
 */
public class VdcConfigMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(
            VdcConfigMigration.class);

    @Override
    public void process() {
        cleanupStaleDbConfig();
        migrateVdcConfigToZk();
    }
    
    private void cleanupStaleDbConfig() {
        coordinatorClient.deletePath(String.format("%s/%s", ZkPath.CONFIG, Constants.DB_CONFIG));
        coordinatorClient.deletePath(String.format("%s/%s", ZkPath.CONFIG, Constants.GEODB_CONFIG));
        log.info("Removed dbconfig/geodbconfig in zk global area successfully");
    }
    
    private void migrateVdcConfigToZk() {
        List<URI> vdcIds = dbClient.queryByType(VirtualDataCenter.class, true);
        for(URI vdcId : vdcIds) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
            if (vdc.getLocal()) {
                continue;
            }
            // Insert vdc info
            ConfigurationImpl vdcConfig = new ConfigurationImpl();
            vdcConfig.setKind(Site.CONFIG_KIND);
            vdcConfig.setId(vdc.getShortId());
            coordinatorClient.persistServiceConfiguration(vdcConfig);
            
            // insert DR active site info to ZK
            Site site = new Site();
            site.setUuid(UUID.randomUUID().toString()); // use a random uuid
            site.setName("Default Active Site");
            site.setVdcShortId(vdc.getShortId());
            site.setSiteShortId(Constants.CONFIG_DR_FIRST_SITE_SHORT_ID);
            site.setHostIPv4AddressMap(vdc.getHostIPv4AddressesMap());
            site.setHostIPv6AddressMap(vdc.getHostIPv6AddressesMap());
            site.setState(SiteState.ACTIVE);
            site.setCreationTime(System.currentTimeMillis());
            site.setVip(vdc.getApiEndpoint());
            site.setNodeCount(vdc.getHostCount());
            
            coordinatorClient.persistServiceConfiguration(site.toConfiguration());
            
            // update Site version in ZK
            SiteInfo siteInfo = new SiteInfo(System.currentTimeMillis(), SiteInfo.NONE);
            coordinatorClient.setTargetInfo(siteInfo);
        }
        log.info("Migrated vdc config from db to zk");
    }
}
