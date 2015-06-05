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

package com.emc.storageos.volumecontroller.impl.utils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy.HitachiTieringPolicy;
import com.emc.storageos.db.client.model.AutoTieringPolicy.VnxFastPolicy;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class DiscoveryUtils {
	
	private static final Logger _log = LoggerFactory.getLogger(DiscoveryUtils.class);
	public static final String UNMANAGED_EXPORT_MASK = "UnManagedExportMask";
	public static final String UNMANAGED_VOLUME = "UnManagedVolume";
	
	/**
     * get Matched Virtual Pools For Pool. 
     * This is called to calculate supported vpools during unmanaged objects discovery
     *
     * @param poolUri
     * @return
     */
    public static StringSet getMatchedVirtualPoolsForPool(DbClient dbClient, URI poolUri,
    		String isThinlyProvisionedUnManagedObject) {
        StringSet vpoolUriSet = new StringSet();
        //We should match all virtual pools as below:
        //1) Virtual pools which have useMatchedPools set to true and have the storage pool in their matched pools
        //2) Virtual pools which have the storage pool in their assigned pools 
        
        URIQueryResultList vpoolMatchedPoolsResultList = new URIQueryResultList();        
       dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getMatchedPoolVirtualPoolConstraint(poolUri), vpoolMatchedPoolsResultList);
       	List<VirtualPool> vPoolsMatchedPools = dbClient.queryObject(VirtualPool.class, vpoolMatchedPoolsResultList); 
       	String provisioningTypeUnManagedObject = UnManagedVolume.SupportedProvisioningType
       			.getProvisioningType(isThinlyProvisionedUnManagedObject);
       	StoragePool storagePool = dbClient.queryObject(StoragePool.class, poolUri);
        for(VirtualPool vPool : vPoolsMatchedPools) {
            if (!VirtualPool.vPoolSpecifiesHighAvailability(vPool)) {
            	List<StoragePool> validPools = VirtualPool.getValidStoragePools(vPool, dbClient, true);
            	for(StoragePool sPool : validPools) {
            		if(sPool.getId().equals(storagePool.getId()) &&
            				provisioningTypeUnManagedObject.equalsIgnoreCase(vPool.getSupportedProvisioningType())) {
            			vpoolUriSet.add(vPool.getId().toString());
            			break;
            		}
            	}
            }
        }
        
        return vpoolUriSet;
    }
    
    // Getting all the vpools
    public static StringSet getMatchedVirtualPoolsForPool(DbClient dbClient, URI poolUri) {
        StringSet vpoolUriSet = new StringSet();
        //We should match all virtual pools as below:
        //1) Virtual pools which have useMatchedPools set to true and have the storage pool in their matched pools
        //2) Virtual pools which have the storage pool in their assigned pools 
        
        URIQueryResultList vpoolMatchedPoolsResultList = new URIQueryResultList();        
       dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getMatchedPoolVirtualPoolConstraint(poolUri), vpoolMatchedPoolsResultList);
        List<VirtualPool> vPoolsMatchedPools = dbClient.queryObject(VirtualPool.class, vpoolMatchedPoolsResultList); 
        StoragePool storagePool = dbClient.queryObject(StoragePool.class, poolUri);
        for(VirtualPool vPool : vPoolsMatchedPools) {
            List<StoragePool> validPools = VirtualPool.getValidStoragePools(vPool, dbClient, true);
            for(StoragePool sPool : validPools) {
                if(sPool.getId().equals(storagePool.getId())) {
                    vpoolUriSet.add(vPool.getId().toString());
                    break;
                }
            }
        }
        
        return vpoolUriSet;
    }

    /**
     * Filters supported vPools in UnManaged Volume based on Auto-Tiering Policy.
     *
     * @param unManagedVolume the UnManaged volume
     * @param policyName the policy name associated with UnManaged volume
     * @param system the system
     * @param dbClient the db client
     */
    public static void filterSupportedVpoolsBasedOnTieringPolicy(
            UnManagedVolume unManagedVolume, String policyName, StorageSystem system, DbClient dbClient) {

        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        StringSet supportedVpoolURIs = unManagedVolumeInformation
                .get(SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString());
        List<String> vPoolsToRemove = new ArrayList<String>();
        if (supportedVpoolURIs != null) {
            Iterator<String> itr = supportedVpoolURIs.iterator();
            while (itr.hasNext()) {
                String uri = itr.next();
                VirtualPool vPool = dbClient.queryObject(VirtualPool.class, URI.create(uri));
                if (vPool != null && !vPool.getInactive()) {
                    //generate unmanaged volume's policyId
                    String autoTierPolicyId = NativeGUIDGenerator
                            .generateAutoTierPolicyNativeGuid(system.getNativeGuid(), policyName,
                                    NativeGUIDGenerator.getTieringPolicyKeyForSystem(system));
                    if (!checkVPoolValidForUnManagedVolumeAutoTieringPolicy(vPool, autoTierPolicyId, system)) {
                        String msg = "Removing vPool %s from SUPPORTED_VPOOL_LIST in UnManagedVolume %s "
                                + "since Auto-tiering Policy %s in UnManaged Volume does not match with vPool's (%s)";
                        _log.info(String.format(msg, new Object[] { uri, unManagedVolume.getId(),
                                autoTierPolicyId, vPool.getAutoTierPolicyName() }));
                        vPoolsToRemove.add(uri);
                    }
                } else {
                    // remove Inactive vPool URI
                    vPoolsToRemove.add(uri);
                }
            }
        }
        for (String uri : vPoolsToRemove) {     // UnManagedVolume object is persisted by caller
            supportedVpoolURIs.remove(uri);
        }
    }

    /**
     * Checks the UnManaged Volume's policy with vPool's policy.
     *
     * @param vPool the vPool
     * @param autoTierPolicyId the auto tier policy id on unmanaged volume
     * @param system the system
     * @return true, if matching, false otherwise
     */
    public static boolean checkVPoolValidForUnManagedVolumeAutoTieringPolicy(
            VirtualPool vPool, String autoTierPolicyId, StorageSystem system) {

        _log.debug("Policy Id: {}, vPool: {}", autoTierPolicyId, vPool);
        boolean policyMatching = false;
        String policyIdfromVPool = vPool.getAutoTierPolicyName();
        if (autoTierPolicyId != null) {
            if (policyIdfromVPool != null) {
                if (vPool.getUniquePolicyNames()
                        || DiscoveredDataObject.Type.vnxblock.name().equalsIgnoreCase(system.getSystemType())) {
                    // Unique Policy names field will not be set for VNX. vPool will have policy name, not the policy's nativeGuid
                    policyIdfromVPool = NativeGUIDGenerator
                            .generateAutoTierPolicyNativeGuid(system.getNativeGuid(),
                                    policyIdfromVPool, NativeGUIDGenerator.getTieringPolicyKeyForSystem(system));
                    _log.debug("Policy Id generated: {}", policyIdfromVPool);
                }
                if (autoTierPolicyId.equalsIgnoreCase(policyIdfromVPool)) {
                    policyMatching = true;
                }
            }
        } else if ((policyIdfromVPool == null) || (policyIdfromVPool.equalsIgnoreCase("none"))) {
            // if policy is not set in both unmanaged volume and vPool. Note
            // that the value in the vpool could be set to "none".
            policyMatching = true;
        }

        // Default policy for VNX - match volume with default policy to vPool with no policy as well
        if (!policyMatching && DiscoveredDataObject.Type.vnxblock.name().equalsIgnoreCase(system.getSystemType())) {
            if (autoTierPolicyId != null && autoTierPolicyId.contains(VnxFastPolicy.DEFAULT_START_HIGH_THEN_AUTOTIER.name()) &&
                    policyIdfromVPool == null) {
                policyMatching = true;
            }
        }

        // Default policy for HDS - match volume with default policy to vPool with no policy as well
        if (!policyMatching && DiscoveredDataObject.Type.hds.name().equalsIgnoreCase(system.getSystemType())) {
            if (autoTierPolicyId != null && autoTierPolicyId.contains(HitachiTieringPolicy.All.name()) &&
                    policyIdfromVPool == null) {
                policyMatching = true;
            }
        }

        return policyMatching;
    }

    public static void setSystemResourcesIncompatible(DbClient dbClient, CoordinatorClient coordinator, URI storageSystemId){
    	// Mark all Pools as incompatible
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePoolConstraint(storageSystemId),
                storagePoolURIs);
        Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
        List<StoragePool> modifiedPools = new ArrayList<StoragePool>();
        while (storagePoolIter.hasNext()) {
           StoragePool pool = dbClient.queryObject(StoragePool.class,storagePoolIter.next());
           modifiedPools.add(pool);
           pool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
           dbClient.persistObject(pool);
        }
        ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVirtualPool(modifiedPools, dbClient, coordinator);;

        // Mark all Ports as incompatible
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystemId),
                storagePortURIs);
        Iterator<URI> storagePortIter = storagePortURIs.iterator();
        while (storagePortIter.hasNext()) {
            StoragePort port = dbClient.queryObject(StoragePort.class,storagePortIter.next());
            port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
            dbClient.persistObject(port);
        }
    }
    
    public static List<StoragePool> checkStoragePoolsNotVisible(List<StoragePool> discoveredPools,  
            DbClient dbClient, URI storageSystemId) {
        List<StoragePool> modifiedPools = new ArrayList<StoragePool>();
        //Get the pools previously discovered
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePoolConstraint(storageSystemId),
                storagePoolURIs);
        Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
        
        List<URI> existingPoolsURI = new ArrayList<URI>();
        while (storagePoolIter.hasNext()) {
            existingPoolsURI.add(storagePoolIter.next());
        }
        
        List<URI> discoveredPoolsURI = new ArrayList<URI>();
        for(StoragePool pool : discoveredPools) {
            discoveredPoolsURI.add(pool.getId());
        }
        
        Set<URI> poolDiff = Sets.difference(new HashSet<URI>(existingPoolsURI), new HashSet<URI>(discoveredPoolsURI));
        
        if(!poolDiff.isEmpty()) {            
            Iterator<StoragePool> storagePoolIt = dbClient.queryIterativeObjects(StoragePool.class, poolDiff, true);
            while (storagePoolIt.hasNext()) {
                StoragePool pool = storagePoolIt.next();
                modifiedPools.add(pool);
                _log.info("Setting discovery status of pool {} : {} as NOTVISIBLE", pool.getLabel(), pool.getId());
                pool.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.NOTVISIBLE.name());                
                dbClient.persistObject(pool);
             }             
        }
        
        return modifiedPools;
    }
    
    public static List<StoragePort> checkStoragePortsNotVisible(List<StoragePort> discoveredPorts,  
            DbClient dbClient, URI storageSystemId) {
        List<StoragePort> modifiedPorts = new ArrayList<StoragePort>();
        //Get the pools previousy discovered
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystemId),
                storagePortURIs);
        Iterator<URI> storagePortIter = storagePortURIs.iterator();
        
        List<URI> existingPortsURI = new ArrayList<URI>();
        while (storagePortIter.hasNext()) {
            existingPortsURI.add(storagePortIter.next());
        }
        
        List<URI> discoveredPortsURI = new ArrayList<URI>();
        for(StoragePort port : discoveredPorts) {
            discoveredPortsURI.add(port.getId());
        }
        
        Set<URI> portsDiff = Sets.difference(new HashSet<URI>(existingPortsURI), new HashSet<URI>(discoveredPortsURI));
        
        if(!portsDiff.isEmpty()) {
            Iterator<StoragePort> storagePortIt = dbClient.queryIterativeObjects(StoragePort.class, portsDiff, true);
            while (storagePortIt.hasNext()) {
                StoragePort port = storagePortIt.next();
                modifiedPorts.add(port);
                _log.info("Setting discovery status of port {} : {} as NOTVISIBLE", port.getLabel(), port.getId());
                port.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.NOTVISIBLE.name());
                dbClient.persistObject(port);
             }
        }
        
        return modifiedPorts;
    }
    
    public static void checkStoragePortsNotVisibleForSMI(List<StoragePort> discoveredPorts, Set<URI> systemsToRunRPConnectivity, 
            List<StoragePort> portsToRunNetworkConnectivity, Map<URI,StoragePool> poolsToMatchWithVpool,
            DbClient dbClient, URI storageSystemId) {
        List<StoragePort> notVisiblePorts = checkStoragePortsNotVisible(discoveredPorts, dbClient, storageSystemId);
        
        //Systems used to run RP connectivity later after runing pool matcher
        if(systemsToRunRPConnectivity != null) {
            systemsToRunRPConnectivity.addAll(StoragePoolAssociationHelper.getStorageSytemsFromPorts(notVisiblePorts, null));
        }
        
        if(poolsToMatchWithVpool != null) {
            List<StoragePool> modifiedPools = StoragePoolAssociationHelper.getStoragePoolsFromPorts(dbClient, null, notVisiblePorts);
            for (StoragePool pool : modifiedPools) {
                // pool matcher will be invoked on this pool
                if (!poolsToMatchWithVpool.containsKey(pool.getId())) {
                    poolsToMatchWithVpool.put(pool.getId(), pool);
                }
            }
        }
        
        //ports used later to run Transport Zone connectivity
        if(portsToRunNetworkConnectivity != null) {
            portsToRunNetworkConnectivity.addAll(notVisiblePorts);
        }       
    }
    
    /**
     * check Storage Volume exists in DB
     * 
     * @param dbClient
     * @param nativeGuid
     * @return
     * @throws IOException
     */
    public static Volume checkStorageVolumeExistsInDB(DbClient dbClient, String nativeGuid)
            throws IOException {
        List<Volume> volumes = CustomQueryUtility.getActiveVolumeByNativeGuid(dbClient, nativeGuid);
        Iterator<Volume> volumesItr = volumes.iterator();
        if (volumesItr.hasNext()) {
            return volumesItr.next();
        }
        return null;
    }
    
    /**
     * check Storage Volume exists in DB
     * 
     * @param dbClient
     * @param nativeGuid
     * @return
     * @throws IOException
     */
    public static BlockSnapshot checkBlockSnapshotExistsInDB(DbClient dbClient, String nativeGuid)
            throws IOException {
        List<BlockSnapshot> snapshots = CustomQueryUtility.getActiveBlockSnapshotByNativeGuid(dbClient, nativeGuid);
        Iterator<BlockSnapshot> snapshotItr = snapshots.iterator();
        if (snapshotItr.hasNext()) {
            return snapshotItr.next();
        }
        return null;
    }
    
    /**
     * check UnManagedVolume exists in DB
     * 
     * @param nativeGuid
     * @param dbClient
     * @return
     * @throws IOException
     */
    public static UnManagedVolume checkUnManagedVolumeExistsInDB(DbClient dbClient, String nativeGuid) {
        URIQueryResultList unManagedVolumeList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeInfoNativeIdConstraint(nativeGuid), unManagedVolumeList);
        if (unManagedVolumeList.iterator().hasNext()) {
            URI unManagedVolumeURI = unManagedVolumeList.iterator().next();
            UnManagedVolume volumeInfo = dbClient.queryObject(UnManagedVolume.class, unManagedVolumeURI);
            if (!volumeInfo.getInactive()) {
                return volumeInfo;
            }
        }
        return null;
    }
    
    /**
     * This method cleans up UnManaged Volumes in DB, which had been deleted manually from the Array
     * 1. Get All UnManagedVolumes from DB
     * 2. Store URIs of unmanaged volumes returned from the Provider in unManagedVolumesBookKeepingList.
     * 3. If unmanaged volume is found only in DB, but not in unManagedVolumesBookKeepingList, then set unmanaged volume to inactive.
     * 
     * DB   | Provider
     * 
     * x,y,z  | y,z.a [a --> new entry has been added but indexes didn't get added yet into DB]
     * 
     * x--> will be set to inactive
     * 
     * @param storageSystem
     * @param discoveredUnManagedVolumes
     * @param dbClient
     * @param partitionManager
     */
    public static void markInActiveUnManagedVolumes(StorageSystem storageSystem,
            Set<URI> discoveredUnManagedVolumes, DbClient dbClient, PartitionManager partitionManager) {

        _log.info(" -- Processing {} discovered UnManaged Volumes Objects from -- {}",
                discoveredUnManagedVolumes.size(), storageSystem.getLabel());
        if (discoveredUnManagedVolumes.isEmpty()) {
            return;
        }
        // Get all available existing unmanaged Volume URIs for this array from DB
        URIQueryResultList allAvailableUnManagedVolumesInDB = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceUnManagedVolumeConstraint(storageSystem.getId()),
                allAvailableUnManagedVolumesInDB);

        Set<URI> unManagedVolumesInDBSet = new HashSet<URI>();
        Iterator<URI> allAvailableUnManagedVolumesItr = allAvailableUnManagedVolumesInDB.iterator();
        while (allAvailableUnManagedVolumesItr.hasNext()) {
            unManagedVolumesInDBSet.add(allAvailableUnManagedVolumesItr.next());
        }
        
        SetView<URI> onlyAvailableinDB =  Sets.difference(unManagedVolumesInDBSet, discoveredUnManagedVolumes);

        _log.info("Diff :" + Joiner.on("\t").join(onlyAvailableinDB));
        if (onlyAvailableinDB.size() > 0) {
            List<UnManagedVolume> unManagedVolumeTobeDeleted = new ArrayList<UnManagedVolume>();
            Iterator<UnManagedVolume> unManagedVolumes =  dbClient.queryIterativeObjects(UnManagedVolume.class, 
                    new ArrayList<URI>(onlyAvailableinDB));

            while (unManagedVolumes.hasNext()) {
                UnManagedVolume volume = unManagedVolumes.next();
                if (null == volume || volume.getInactive()) {
                    continue;
                }

                _log.info("Setting unManagedVolume {} inactive",volume.getId());
                volume.setStoragePoolUri(NullColumnValueGetter.getNullURI());
                volume.setStorageSystemUri(NullColumnValueGetter.getNullURI());                
                volume.setInactive(true);
                unManagedVolumeTobeDeleted.add(volume);
            }
            if (unManagedVolumeTobeDeleted.size() > 0 ) {
                partitionManager.updateAndReIndexInBatches(unManagedVolumeTobeDeleted, 1000,
                        dbClient, UNMANAGED_VOLUME);
            }
        }
    }
    
    public static void markInActiveUnManagedExportMask(URI storageSystemUri,
            Set<URI> discoveredUnManagedExportMasks, DbClient dbClient, PartitionManager partitionManager) {
        
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageSystemUnManagedExportMaskConstraint(storageSystemUri), result);
        Set<URI> allMasksInDatabase = new HashSet<URI>();
        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            allMasksInDatabase.add(it.next());
        }

        SetView<URI> onlyAvailableinDB =  Sets.difference(allMasksInDatabase, discoveredUnManagedExportMasks);
        
        if (onlyAvailableinDB.size() > 0) {
            _log.info("these UnManagedExportMasks are orphaned and will be cleaned up:" 
                    + Joiner.on("\t").join(onlyAvailableinDB));

            List<UnManagedExportMask> unManagedExportMasksToBeDeleted = new ArrayList<UnManagedExportMask>();
            Iterator<UnManagedExportMask> unManagedExportMasks =  
                dbClient.queryIterativeObjects(UnManagedExportMask.class, new ArrayList<URI>(onlyAvailableinDB));

            while (unManagedExportMasks.hasNext()) {
                
                UnManagedExportMask uem = unManagedExportMasks.next();
                if (null == uem || uem.getInactive()) {
                    continue;
                }

                _log.info("Setting UnManagedExportMask {} inactive", uem.getMaskingViewPath());
                uem.setStorageSystemUri(NullColumnValueGetter.getNullURI());
                uem.setInactive(true);
                unManagedExportMasksToBeDeleted.add(uem);
            }
            if (unManagedExportMasksToBeDeleted.size() > 0 ) {
                partitionManager.updateAndReIndexInBatches(unManagedExportMasksToBeDeleted, Constants.DEFAULT_PARTITION_SIZE,
                        dbClient, UNMANAGED_EXPORT_MASK);
            }
        }
        
    }
}
