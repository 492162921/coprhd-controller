/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;

/**
 * Migration handler to initialize the assigned virtual arrays field for the
 * network.
 */
public class NetworkAssignedVirtualArraysInitializer extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(NetworkAssignedVirtualArraysInitializer.class);
    
    @SuppressWarnings("deprecation")
    @Override
    public void process() {
        DbClient dbClient = getDbClient();
        List<URI> networkURIs = dbClient.queryByType(Network.class, false);
        Iterator<Network> networksIter = dbClient.queryIterativeObjects(Network.class, networkURIs);
        while (networksIter.hasNext()) {
            Network network = networksIter.next();
            String networkId = network.getId().toString();
            log.info("Examining Network (id={}) for upgrade", networkId);
            URI networkVArrayURI = network.getVirtualArray();
            if (!NullColumnValueGetter.isNullURI(networkVArrayURI)) {
                // Update the new field.
                network.setAssignedVirtualArrays(StringSetUtil
                    .uriListToStringSet(Collections.singletonList(networkVArrayURI)));
                dbClient.updateAndReindexObject(network);
                log.info("Set assigned virtual arrays for network (id={}) to virtual array (id={})",
                    networkId, networkVArrayURI.toString());
            }
        }
    }
}
