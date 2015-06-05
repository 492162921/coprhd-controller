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
package com.emc.storageos.security.keystore.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.security.SerializerUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * Helper class for storing and retrieving configurations in coordinator.
 * This class handles the use of InterProcessLock such that all updates on
 * the coordinator configuration are synced accross the cluseter. Also,
 * InterProcessLocks are being reused according to the lock name given, 
 * which makes it possible to acquire the same lock several times on a single
 * thread. 
 */
public class CoordinatorConfigStoringHelper {

    private static Logger log = LoggerFactory.getLogger(CoordinatorConfigStoringHelper.class);
    private CoordinatorClient coordinator;
    private final Map<String, InterProcessLock> nameLockMap;

    public CoordinatorConfigStoringHelper() {
        nameLockMap = new HashMap<String, InterProcessLock>();
    }

    public CoordinatorConfigStoringHelper(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
        nameLockMap = new HashMap<String, InterProcessLock>();
    }

    /**
     * 
     * Creates or updates a new entry of the specified type in coordinator.
     * 
     * @param objToPersist
     *            the object to store in coordinator
     * @param lockName
     *            the name of the lock to use while storing this object
     *            If passed as Null, lock is assumed to be already owned
     * @param configKInd
     * @param configId
     * @param ConfigKey
     * @throws Exception
     */
    public void createOrUpdateConfig(Object objToPersist, String lockName,
            String configKInd, String configId, String ConfigKey) throws Exception {
        InterProcessLock lock = acquireLock(lockName);
        try {
            if (lock != null) {
                Configuration config = coordinator.queryConfiguration(configKInd, configId);
                ConfigurationImpl configImpl = null;
                if (config == null) {
                    configImpl = new ConfigurationImpl();
                    configImpl.setId(configId);
                    configImpl.setKind(configKInd);
                    log.debug("Creating new config");
                } else {
                    configImpl = (ConfigurationImpl) config;

                    if (config.getKind() == null) {
                        ((ConfigurationImpl) config).setKind(configKInd);
                    }
                    if (config.getId() == null) {
                        ((ConfigurationImpl) config).setId(configId);
                    }

                    log.debug("Updating existing config");

                }
                configImpl.setConfig(ConfigKey,
                        SerializerUtils.serializeAsBase64EncodedString(objToPersist));
                coordinator.persistServiceConfiguration(configImpl);
                log.debug("Updated config successfully");
            }
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * Reads object of the specified kind from coordinator and deserializes it.
     * 
     * @param configKind
     * @param configId
     * @param ConfigKey
     * @return the retrieved object or null if not found
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public <T> T readConfig(String configKind, String configId, String ConfigKey)
            throws IOException, ClassNotFoundException {
        Configuration config = coordinator.queryConfiguration(configKind, configId);
        if (config == null || config.getConfig(ConfigKey) == null) {
            log.debug("Config of kind " + configKind + " and id " + configId
                    + "not found");
            return null;
        }
        String serializedConfig = config.getConfig(ConfigKey);
        @SuppressWarnings("unchecked")
        T retObj = (T) SerializerUtils.deserialize(serializedConfig);
        return retObj;
    }

    /**
     * Acquires an interprocess lock
     * 
     * @param lockName
     *            the lock to acquire
     * @return the acquired lock
     * @throws Exception
     *             if failed to acquire the lock
     */
    public synchronized InterProcessLock acquireLock(String lockName) throws Exception {
        InterProcessLock lock = nameLockMap.get(lockName);
        if (lock == null) {
            lock = coordinator.getLock(lockName);
            nameLockMap.put(lockName, lock);
        }
        lock.acquire();
        return lock;
    }

    /**
     * release the specified lock
     * 
     * @param lock
     *            the lock to release
     */
    public void releaseLock(InterProcessLock lock) {
        try {
            if (lock != null) {
                lock.release();
            }
        }   catch (Exception e) {
            log.error("Could not release lock");
        }
    }

    /**
     * Removes the specified config from coordinator
     * 
     * @param lockName
     *            the name of the lock to use while removing this object
     * @param configKInd
     * @param configId
     * @throws Exception
     */
    public void removeConfig(String lockName, String configKInd, String configId)
            throws Exception {
        InterProcessLock lock = acquireLock(lockName);
        try {
            Configuration config = coordinator.queryConfiguration(configKInd, configId);
            if (config != null) {
                coordinator.removeServiceConfiguration(config);
                log.debug("removed config successfully");
            } else {
                log.debug("config " + configId + " of kind " + configKInd
                        + " was not removed since it could not be found");
            }
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * 
     * Removes all configs with the specified kind from coordinator
     * 
     * @param lockName
     *            the name of the lock to use while removing this object
     * @param configKInd
     * @param configId
     * @param ConfigKey
     * @throws Exception
     */
    public void removeAllConfigOfKInd(String lockName, String configKInd)
            throws Exception {
        InterProcessLock lock = acquireLock(lockName);
        try {
            List<Configuration> configs = coordinator.queryAllConfiguration(configKInd);
            if (!CollectionUtils.isEmpty(configs)) {
                for (Configuration configuration : configs) {
                    coordinator.removeServiceConfiguration(configuration);
                }
            } else {
                log.debug("configs of kind " + configKInd
                        + " were not removed since none were found");
            }
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * Reads all objects of the specified kind from coordinator
     * 
     * @param configKind
     * @param configId
     * @return the retrieved objects list or null if not found
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public <T> Map<String, T> readAllConfigs(String configKind, String configKey)
            throws IOException, ClassNotFoundException {
        List<Configuration> configsList = coordinator.queryAllConfiguration(configKind);
        Map<String, T> returnedObjects = new HashMap<String, T>();
        if (CollectionUtils.isEmpty(configsList)) {
            log.debug("No config of kind " + configKind + " found");
            return returnedObjects;
        }
        for (Configuration config : configsList) {
            String serializedConfig = config.getConfig(configKey);
            if (serializedConfig != null) {
                @SuppressWarnings("unchecked")
                T deserialize = (T) SerializerUtils.deserialize(serializedConfig);
                returnedObjects.put(config.getId(), deserialize);
            }
        }
        return returnedObjects;
    }

    /**
     * @param coordinator the coordinator to set
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

}
