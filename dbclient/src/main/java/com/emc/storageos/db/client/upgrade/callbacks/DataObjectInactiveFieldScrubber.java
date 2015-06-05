/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
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
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.common.PackageScanner;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * Migration handler to update the missed settting of DataObject.inactive field
 */
public class DataObjectInactiveFieldScrubber extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(DataObjectInactiveFieldScrubber.class);
    private String[] packages = {"com.emc.storageos.db.client.model"};

    public class InactiveCheckScanner extends PackageScanner {

        public InactiveCheckScanner(String... packages) {
            super(packages);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void processClass(Class clazz) {
            if (!DataObject.class.isAssignableFrom(clazz) ||
                clazz.getAnnotation(NoInactiveIndex.class) != null) {
                return;
            }
            List<URI> keyList = ((InternalDbClient)getDbClient()).getUpdateList(clazz);
            if (keyList == null || keyList.size() == 0) {
                return;
            }
            updateInactiveField(clazz, keyList);
        }
    }

    @Override
    public void process() {
        InactiveCheckScanner scanner = new InactiveCheckScanner(packages);
        scanner.scan(Cf.class);
    }

    /**
     * Update the missed setting of "inactive field" for all data object
     */     
    private <T extends DataObject> void updateInactiveField(Class<T> clazz, List<URI> keyList) {
        DbClient dbClient = getDbClient();
        log.info("update inactive field for class: {}", clazz.getSimpleName());

        T object;
        for (URI key : keyList) {
            try {
                object = clazz.newInstance();
                object.setId(key);
                object.setInactive(false);
            } catch (Exception e) {
                log.error("create new object of class({}) failed. e=",
                     clazz.getSimpleName(), e);
                throw new IllegalStateException(e); 
            }
            dbClient.updateAndReindexObject(object);
            log.info("Update the inactive field of object(cf={}, id={}) to false", 
                    object.getClass().getName(), object.getId());
        }
    }
} 
