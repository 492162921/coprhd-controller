/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.gc;

import java.net.URI;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.db.client.model.NoInactiveIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import com.netflix.astyanax.util.TimeUUIDUtils;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.db.common.DependencyTracker;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * Runnable implementation for the GC threads
 */
abstract class GarbageCollectionRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(GarbageCollectionRunnable.class);
    final static String GC_LOCK_PREFIX="gc/";
    final static long MIN_TO_MICROSECS = 60 * 1000 * 1000;
    final protected Class<? extends DataObject> type;
    final protected DbClient dbClient;
    final private long timeStartMarker;
    final protected DependencyChecker dependencyChecker;
    final private CoordinatorClient coordinator;

    GarbageCollectionRunnable(DbClient dbClient, Class<? extends DataObject> type,
                              DependencyTracker dependencyTracker, int gcDelayMins,
                              CoordinatorClient coordinator) {
        this.type = type;
        this.dbClient = dbClient;

        // Now - gcDelay
        if (gcDelayMins > 0) {
            timeStartMarker = TimeUUIDUtils.getMicrosTimeFromUUID(TimeUUIDUtils.getUniqueTimeUUIDinMicros())
                - (gcDelayMins * MIN_TO_MICROSECS);
        } else {
            timeStartMarker = 0;
        }

        dependencyChecker = new DependencyChecker(dbClient, dependencyTracker);
        this.coordinator = coordinator;
    }

    /**
     * Check if a resource could be deleted from DB
     * @param id  the resource ID
     * @return true if the resource an be deleted
     */
    protected abstract boolean canBeGC(URI id);

    /**
     * get list of uris to check
     * @param clazz
     */
    private URIQueryResultList getDecommissionedObjectsOfType(Class<? extends DataObject> clazz) {
        URIQueryResultList list = new URIQueryResultList();
        dbClient.queryInactiveObjects(clazz, timeStartMarker, list);
        return list;
    }

    @Override
    public void run() {
        URIQueryResultList list;
        InterProcessLock lock = null;

        log.debug("Starting GC loop: type: {}", type.getSimpleName());

        try { 
            log.debug("try to get ZK lock {}", type.getName());

            lock = getLockForGC();

            if (lock == null)
                return;
        } catch (Exception e) {
            log.info("Failed to acquire ZK lock for {} Exception e=", type, e);
            return;
        }

        log.info("Lock the class {}", type.getName());
        try {
            list = getDecommissionedObjectsOfType(type);

            int found =0, deleted = 0;
            for(Iterator<URI> iterator = list.iterator(); iterator.hasNext(); ) {
                URI uri = iterator.next();
                found++;
                log.debug("GC checks dependencies for {}", uri);
                try {
                    if (!canBeGC(uri))
                        continue;

                    DataObject obj = dbClient.queryObject(type, uri);
                    if (obj != null) {
                        log.info("No dependencies found. Removing {}", uri);
                        dbClient.removeObject(obj);
                        deleted++;
                    }
                } catch (DatabaseException ex) {
                    log.warn("Exception from database access: ", ex);
                    // To Do - we should skip the whole loop and retry later?
                }
            }

            if (found > 0) {
                log.info(String.format("Done GC loop: type: %s, processed %s, deleted %s",
                type.getSimpleName(), found, deleted));
            }
        } catch (Exception e) {
            log.error("Exception e=" , e);
        } finally {
            releaseLockForGC(lock);
        }
    }

    private InterProcessLock getLockForGC() {
        InterProcessLock lock = null;
        try {
            String lockName = GC_LOCK_PREFIX + type.getName();
            log.debug("try to get ZK lock {}", lockName);

            lock = coordinator.getLock(lockName);
            if (lock.acquire(0, TimeUnit.SECONDS) == false) {// try to get the lock timeout=0
                log.info("Failed to get ZK lock for {}", type.getName());
                return null; //failed to get the lock
            }

            log.debug("Lock the class {}", type.getName());
        } catch (Exception e) {
            log.info("Failed to acquire GC lock for Geo class {} Exception e=", type, e);
            lock = null;
        }

        return lock;
    }

    private void releaseLockForGC(InterProcessLock lock) {
        try {
            lock.release();
            log.debug("Release the ZK lock of {}", type.getName());
        }catch (Exception e) {
            log.error("Failed to release the lock for class {} e=", type, e);
        }
    }
}
