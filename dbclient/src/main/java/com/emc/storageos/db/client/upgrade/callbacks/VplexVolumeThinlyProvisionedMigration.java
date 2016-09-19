/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;

/**
 * If we are upgrading from any version before 3.5, the thinlyProvisioned 
 * property should be set to false on any ViPR-managed volumes.
 * 
 * Before 3.5, the flag on VPLEX virtual volumes would just have been set
 * however the owning VirtualPool's thin provisioning property was set, 
 * even though VPLEX didn't use this flag.  Support for thin provisioning
 * was added to VPLEX version supported at the same time as ViPR 3.5.  So,
 * any volumes created before that would need to be thinlyProvisioned=false. 
 * 
 * @author beachn
 * @since 3.5
 */
public class VplexVolumeThinlyProvisionedMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(VplexVolumeThinlyProvisionedMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumesIter = dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        int volumeUpdatedCount = 0;

        // cache a list of vplex URIs for performance reasons
        List<URI> vplexUris = new ArrayList<URI>();
        List<StorageSystem> vplexes = VPlexControllerUtils.getAllVplexStorageSystems(dbClient);
        for (StorageSystem vplex : vplexes) {
            if (null != vplex) {
                vplexUris.add(vplex.getId());
            }
        }
        logger.info("found {} vplex storage systems in the database", vplexUris.size());

        while (volumesIter.hasNext()) {
            Volume volume = volumesIter.next();
            URI systemURI = volume.getStorageController();
            if (!NullColumnValueGetter.isNullURI(systemURI)) {
                if (vplexUris.contains(systemURI)) {
                    // This is a VPLEX volume. If we are upgrading from any version
                    // before 3.5, if the thinlyProvisioned property is true, it should 
                    // be set to false on any ViPR-managed volumes.
                    if (volume.getThinlyProvisioned()) {
                        logger.info("updating thinlyProvisioned property on volume {} to false", volume.forDisplay());
                        volume.setThinlyProvisioned(false);
                        dbClient.updateObject(volume);
                        volumeUpdatedCount++;
                    }
                }
            }
        }
        logger.info("VplexVolumeThinlyProvisionedMigration completed, updated thinlyProvisioned to false on {} volumes", 
                volumeUpdatedCount);
    }

}
