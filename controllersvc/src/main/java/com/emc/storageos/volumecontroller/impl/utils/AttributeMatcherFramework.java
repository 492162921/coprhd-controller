/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.emc.storageos.volumecontroller.AttributeMatcher;

/**
 * AttributeMatcherFramework is an framework class which abstract the matcher execution.
 * All clients should use this class to invoke matchAttributes to run matcher algorithm.
 * All the matchers are grouped by its nature. Each group will be executed in the order
 * they have added to the LinkedList. Since the beans are injected to a LinkedList using
 * spring configuration, we always guarantee the order.
 * The current sequence of attributeMatcher execution:
 *   1. ActivePoolMatcher
 *   2. NeighborhoodsMatcher
 *   3. CoSTypeAttributeMatcher
 *   4. ProtocolsAttrMatcher
 *
 */
public class AttributeMatcherFramework implements ApplicationContextAware {

    private static final Logger _logger = LoggerFactory
            .getLogger(AttributeMatcherFramework.class);
    private static volatile ApplicationContext _context;

    @Override
    public void setApplicationContext(ApplicationContext appContext)
        throws BeansException {
      _context = appContext;
    }

    public static ApplicationContext getApplicationContext() {
      return _context;
    }

    /**
     * Match all attributes of CoS container & volumeParam container values against the given pools.
     *
     * @param allPools : list of pools
     * @param cos : vpool
     * @param objValueToCompare : volumeParam container values.
     * @param dbClient
     * @param matcherGroupName : groupName to execute the matchers.
     *                           matchers are grouped by its relativity
     */
    public List<StoragePool> matchAttributes(List<StoragePool> allPools, Map<String, Object> attributeMap,
            DbClient dbClient, CoordinatorClient coordinator, String matcherGroupName) {
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        matchedPools.addAll(allPools);
        try {
            _logger.info("Starting execution of {} group matchers .", matcherGroupName);
            @SuppressWarnings("unchecked")
            List<AttributeMatcher> attrMatcherList = (List<AttributeMatcher>) getBeanFromContext(matcherGroupName);
            ObjectLocalCache cache = new ObjectLocalCache(dbClient);
            for (AttributeMatcher matcher : attrMatcherList) {
                int poolSizeAtTheStart = matchedPools.size();
                if (!matchedPools.isEmpty()) {
                    _logger.debug("passing {} pools to match", matchedPools.size());
                    matcher.setObjectCache(cache);
                    matcher.setCoordinatorClient(coordinator);
                    matchedPools = matcher.runMatchStoragePools(matchedPools, attributeMap);
                    if (matchedPools.isEmpty()) {
                        _logger.info(String.format("Failed to find match because of %s",
                                matcher.getClass().getSimpleName()));
                    } else if (matchedPools.size() < poolSizeAtTheStart) {
                        _logger.info(String.format("%s eliminated %d pools from the matched list",
                                matcher.getClass().getSimpleName(), poolSizeAtTheStart-matchedPools.size()));
                    }
                } else {
                    _logger.info("No storage pools found matching with attributeMap passed");
                    break;
                }
            }
            cache.clearCache();
        } catch (Exception ex) {
            // Clearing the matched pools as there is an exception occurred while processing.
            matchedPools.clear();
            _logger.error("Exception occurred while matching pools with vPools", ex);
        } finally {
            _logger.info("Ended execution of {} group matchers .", matcherGroupName);
        }
        return matchedPools;
    }
    /**
     * Sometimes context is not loading properly resulting the beanFactory to null.
     * To avoid this, we should reload the context using refresh.
     * and the return the bean by its matcherGroupName.
     * 
     * @param matcherGroupName
     * @return beanObj
     */
    private Object getBeanFromContext(String matcherGroupName) {
       Object beanObj = _context.getBean(matcherGroupName);
       if (null == beanObj) {
           _logger.error("No bean found for groupName {0} to match Pools for give attributesMap", matcherGroupName);
           throw new ServiceCodeException(ServiceCode.CONTROLLER_STORAGE_ERROR,
                   "No bean found for groupName {0} to match Pools for give attributesMap", new Object[]{matcherGroupName});
       }
       return beanObj;
    }

    /**
     * Find the available attributes in a given varray.
     * @param vArrayId
     * @param neighborhoodPools
     * @param dbClient
     * @param matcherGroupName
     */
    public Map<String, Set<String>> getAvailableAttributes(URI vArrayId, List<StoragePool> neighborhoodPools,
                                                           ObjectLocalCache cache, String matcherGroupName) {
        Map<String, Set<String>> vArrayAvailableAttrs = new HashMap<String, Set<String>>();
        try {
            @SuppressWarnings("unchecked")
            List<AttributeMatcher> attrMatcherList = (List<AttributeMatcher>) getBeanFromContext(matcherGroupName);
            for (AttributeMatcher matcher : attrMatcherList) {
                matcher.setObjectCache(cache);
                Map<String, Set<String>> availableAttribute = matcher.getAvailableAttribute(neighborhoodPools,
                                            vArrayId);
                if (!availableAttribute.isEmpty()) {
                    _logger.info("Found available attributes using matcher {}", matcher);
                    vArrayAvailableAttrs.putAll(availableAttribute);
                }
            }
            _logger.info("Found {} available attributes for vArray {}", vArrayAvailableAttrs, vArrayId);
        } catch (Exception ex) {
            _logger.error("Exception occurred while getting available attributes for vArray {}", vArrayId, ex);
            vArrayAvailableAttrs.clear();
            throw new ServiceCodeException(ServiceCode.CONTROLLER_STORAGE_ERROR,
                    "Exception occurred while getting available attributes for vArray.", new Object[]{vArrayId});
            
        }
        return vArrayAvailableAttrs;
    }
}
