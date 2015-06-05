/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.ObjectStore;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.client.upgrade.BaseDefaultMigrationCallback;

public class GeoDbMigrationCallback extends BaseDefaultMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(GeoDbMigrationCallback.class);

    @Override
    public void process() {
        
        if (cfClass == null || annotation == null) {
            // this callback has not been set up; skip it.
            throw DatabaseException.fatals.failedDuringUpgrade("Unexpected state: callback not setup",
                    null);
        }

        if (! annotation.annotationType().equals(DbKeyspace.class)) {
            throw DatabaseException.fatals.failedDuringUpgrade("Unexpected annotation: only support" +
                    " @DbKeyspace", null);
        }

        String cfName = cfClass.getCanonicalName();
        if (! DataObject.class.isAssignableFrom(cfClass)) {
            throw DatabaseException.fatals.failedDuringUpgrade("Unexpected CF type: " + cfName, null);
        }

        // No need to do anything if the DbKeyspace is set to LOCAL
        DbKeyspace keyspace = (DbKeyspace) cfClass.getAnnotation(annotation.annotationType());
        if (keyspace.value().equals(DbKeyspace.Keyspaces.LOCAL)) {
            log.info("No need to do anything for CF {}", cfName);
            return;
        }

        log.info("Copying db records for class: {} from local db into geodb", cfName);
        try {
            getInternalDbClient().migrateToGeoDb(cfClass);
        } catch (Exception e) {
            log.error("GeoDbMigrationCallback migration failed", e);
            throw DatabaseException.fatals.failedDuringUpgrade( "db schema migration error: failed" +
                    "to migrate CF " + cfName + " into geodb", e);
        }
        log.info("migrate on global resource {} finished", cfClass.getSimpleName());
    }

}
