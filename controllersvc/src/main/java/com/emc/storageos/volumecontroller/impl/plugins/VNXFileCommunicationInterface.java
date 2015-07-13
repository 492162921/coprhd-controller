/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.ShareACL;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExport;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExportMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Namespace;
import com.emc.storageos.plugins.common.domainmodel.NamespaceList;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileCollectionException;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.vnx.xmlapi.VNXControlStation;
import com.emc.storageos.vnx.xmlapi.VNXCifsServer;
import com.emc.storageos.vnx.xmlapi.VNXDataMover;
import com.emc.storageos.vnx.xmlapi.VNXDataMoverIntf;
import com.emc.storageos.vnx.xmlapi.VNXFileSshApi;
import com.emc.storageos.vnx.xmlapi.VNXException;
import com.emc.storageos.vnx.xmlapi.VNXFileSystem;
import com.emc.storageos.vnx.xmlapi.VNXStoragePool;
import com.emc.storageos.vnx.xmlapi.VNXVdm;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileDiscExecutor;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileExecutor;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.UnManagedExportVerificationUtility;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.iwave.ext.netapp.model.ExportsHostnameInfo;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.SecurityRuleInfo;

/**
 * VNXFileCommunicationInterface class is an implementation of
 * CommunicationInterface which is responsible to collect statistics from VNX
 * File using XHMP/XMLAPI interface.
 * 
 */
public class VNXFileCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    /**
     * Logger instance to log messages.
     */
    private static final Logger _logger = LoggerFactory.getLogger(VNXFileCommunicationInterface.class);
    private static final String METERINGFILE = "metering-file";
    private static final String DM_ROLE_STANDBY = "standby";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NEW = "new";
    private static final String EXISTING = "existing";
    private static final String VIRTUAL = "VIRTUAL";
    private static final String PHYSICAL = "PHYSICAL";
    private static final Integer MAX_UMFS_RECORD_SIZE = 1000;
    private static final String UNMANAGED_EXPORT_RULE = "UnManagedExportRule";
    
    private static int BYTESCONV = 1024;  // VNX defaults to M and apparently Bourne wants K.

    /**
     * Executor to execute the operations.
     */
    private VNXFileExecutor _executor;
    private NamespaceList _namespaces;

    private VNXFileDiscExecutor _discExecutor;
    private NamespaceList       _discNamespaces;

    /**
     * Construct the map of input attributes which will be used during the
     * execution and processing the result.
     * 
     * @param accessProfile
     */
    private void populateMap(
            final AccessProfile accessProfile) {
        _logger.debug("Populating input attributes in the map.");
        _keyMap.put(VNXFileConstants.DEVICETYPE, accessProfile.getSystemType());
        _keyMap.put(VNXFileConstants.DBCLIENT, _dbClient);
        _keyMap.put(VNXFileConstants.USERNAME, accessProfile.getUserName());
        _keyMap.put(VNXFileConstants.USER_PASS_WORD, accessProfile.getPassword());
        _keyMap.put(VNXFileConstants.URI, getServerUri(accessProfile));
        _keyMap.put(VNXFileConstants.PORTNUMBER, accessProfile.getPortNumber());
        _keyMap.put(Constants._Stats, new LinkedList<Stat>());
        _keyMap.put(Constants.ACCESSPROFILE, accessProfile);
        _keyMap.put(Constants._serialID, accessProfile.getserialID()); 
        _keyMap.put(Constants._nativeGUIDs, Sets.newHashSet());
        _keyMap.put(VNXFileConstants.AUTHURI, getLoginUri(accessProfile));
        String globalCacheKey = accessProfile.getserialID() + Constants._minusDelimiter
                + Constants._File;
        _keyMap.put(Constants._globalCacheKey, globalCacheKey);
        _keyMap.put(Constants.PROPS, accessProfile.getProps());
        if(_executor != null){
            _executor.setKeyMap(_keyMap);
            _logger.debug("Map set on executor....");
        }
    }

    /**
     * return the XML API Server uri.
     * 
     * @param accessProfile
     *            : accessProfile to get the credentials.
     * @return uri. 
     */
    private String getServerUri(final AccessProfile accessProfile) {
        try {
        	final URI deviceURI = new URI("https", accessProfile.getIpAddress(), "/servlets/CelerraManagementServices", null);
        	return deviceURI.toString();
        } catch (URISyntaxException ex) {
        	_logger.error("Error while creating server uri for IP {}", accessProfile.getIpAddress());
        }
        
        return "";
        
    }

    /**
     * return the XML API Server Login uri.
     * 
     * @param accessProfile
     *            : accessProfile to get the credentials.
     * @return uri.
     */
    private String getLoginUri(final AccessProfile accessProfile)  {
        try {
	    	final URI deviceURI = new URI("https", accessProfile.getIpAddress(), "/Login", null);
	        return deviceURI.toString();
        } catch (URISyntaxException ex) {
        	_logger.error("Error while creating login uri for IP {}", accessProfile.getIpAddress());
        }
        
        return "";
    }

    /**
     * Stop the Plug-in Thread by gracefully clearing allocated resources.
     */
    @Override
    public void cleanup() {
        _logger.info("Stopping the Plugin Thread and clearing Resources");
        releaseResources();
    }

    /**
     * return the VNXFileExecutor.
     * 
     * @return the _executor
     */
    public VNXFileExecutor getExecutor() {
        return _executor;
    }

    /**
     * set the VNXFileExecutor.
     * 
     * @param executor
     *            the _executor to set
     */
    public void setExecutor(final VNXFileExecutor executor) {
        _executor = executor;
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        try {
            _logger.info("Start collecting statistics for ip address {}",
                    accessProfile.getIpAddress());
            // construct the map and use the request attributes
            // to execute operations & process the result.
            populateMap(accessProfile);
            // Read the operations and execute them.
            _executor.execute((Namespace) _namespaces.getNsList().get(METERINGFILE));
            dumpStatRecords();
            injectStats();
            _logger.info("End collecting statistics for ip address {}",
                    accessProfile.getIpAddress());
        } finally {
            releaseResources();
        }
    }

    /**
     * releaseResources
     */
    private void releaseResources() {
        _executor = null;
        _namespaces = null;
    }

    public void set_namespaces(NamespaceList namespaces) {
        _namespaces = namespaces;
    }
    
    public NamespaceList get_namespaces() {
        return _namespaces;
    }

    /**
     * Discover a VNX File Storage System.  Query the Control Station, Storage Pools, Data Movers, and the
     * Network Interfaces for each Data Mover.
     *
     * @param accessProfile    access profile contains credentials to contact the device.
     * @throws BaseCollectionException
     */
    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        _logger.info("Access Profile Details :  IpAddress : PortNumber : {}, namespace : {}",
                accessProfile.getIpAddress() +":" + accessProfile.getPortNumber(),
                accessProfile.getnamespace());

        if ((null != accessProfile.getnamespace())
                && (accessProfile.getnamespace()
                .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS
                        .toString()))) {
            discoverUmanagedFileSystems(accessProfile);
            //discoverUnmanagedExports(accessProfile);
            discoverUnmanagedNewExports(accessProfile);
            discoverUnManagedCifsShares(accessProfile);
        } else {
            discoverAll(accessProfile);
        }
    }

    public void discoverAll(AccessProfile accessProfile) throws BaseCollectionException {
        URI storageSystemId         = null;
        StorageSystem storageSystem = null;
        String detailedStatusMessage = "Unknown Status";
        
        try {
            _logger.info("Access Profile Details :  IpAddress : {}, PortNumber : {}", accessProfile.getIpAddress(), accessProfile.getPortNumber());
            storageSystemId = accessProfile.getSystemId();
            storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
            // Retrieve control station information.
            discoverControlStation(storageSystem);
            _dbClient.persistObject(storageSystem);
            if (!storageSystem.getReachableStatus())  {
                throw new VNXFileCollectionException("Failed to connect to " + storageSystem.getIpAddress());
            }

            //Get All Existing DataMovers
            Map<String, StorageHADomain> allExistingDataMovers = getAllDataMovers(storageSystem);
            for(StorageHADomain activeDM : allExistingDataMovers.values()) {
                _logger.info("Existing DataMovers in database {}", activeDM.getName());
            }


            // Discover port groups (data movers)
            StringSet fileSharingProtocols = new StringSet();
            Map<String, List<StorageHADomain>> groups = discoverPortGroups(storageSystem, fileSharingProtocols);
            _logger.info("No of newly discovered groups {}", groups.get(NEW).size());
            _logger.info("No of existing discovered groups {}", groups.get(EXISTING).size());
            if(!groups.get(NEW).isEmpty()){
                _dbClient.createObject(groups.get(NEW));
                for(StorageHADomain newDm:groups.get(NEW)){
                    _logger.info("New DM {} ", newDm.getAdapterName());
                }
            }

            if(!groups.get(EXISTING).isEmpty()){
                _dbClient.persistObject(groups.get(EXISTING));
                for(StorageHADomain existingDm:groups.get(EXISTING)){
                    _logger.info("Existing DM {} ", existingDm.getAdapterName());
                }
            }

            // Discover storage pools.
            List<StoragePool> poolsToMatchWithVpool = new ArrayList<StoragePool>();
            List<StoragePool> allPools = new ArrayList<StoragePool>();
            Map<String, List<StoragePool>> pools =
                    discoverStoragePools(storageSystem, poolsToMatchWithVpool, fileSharingProtocols);

            _logger.info("No of newly discovered pools {}", pools.get(NEW).size());
            _logger.info("No of existing discovered pools {}", pools.get(EXISTING).size());
            if(!pools.get(NEW).isEmpty()){
                allPools.addAll(pools.get(NEW));
                _dbClient.createObject(pools.get(NEW));
            }

            if(!pools.get(EXISTING).isEmpty()){
                allPools.addAll(pools.get(EXISTING));
                _dbClient.persistObject(pools.get(EXISTING));
            }
            List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(
                    allPools, _dbClient, storageSystemId);
            if (notVisiblePools != null && !notVisiblePools.isEmpty()) {
                poolsToMatchWithVpool.addAll(notVisiblePools);
            }
            // Keep a set of active data movers.  Data movers in 'standby' state are not added to the
            // database since they cannot be used in this state.
            Set<StorageHADomain> activeDataMovers = new HashSet<StorageHADomain>();
            activeDataMovers.addAll(groups.get(NEW));
            activeDataMovers.addAll(groups.get(EXISTING));

            int i = 0;
            for(StorageHADomain activeDM : activeDataMovers) {
                _logger.info("DataMover {} : {}", i++, activeDM.getName());
            }

            // Discover ports (data mover interfaces) with the data movers in the active set.
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem, activeDataMovers);

            _logger.info("No of newly discovered port {}", ports.get(NEW).size());
            _logger.info("No of existing discovered port {}", ports.get(EXISTING).size());
            if(!ports.get(NEW).isEmpty()){
                _dbClient.createObject(ports.get(NEW));
            }

            if(!ports.get(EXISTING).isEmpty()){
                _dbClient.persistObject(ports.get(EXISTING));
            }
            
            //Discover VDM and Ports

            Map<String, StorageHADomain> allVdmsInDb = this.getAllVDMs(storageSystem);

            for(StorageHADomain activeVDM : allVdmsInDb.values()) {
                _logger.info("Existing DataMovers in the Database {}", activeVDM.getName());
            }

            Map<String, List<StorageHADomain>> vdms = discoverVdmPortGroups(storageSystem, activeDataMovers);
            _logger.info("No of newly Vdm discovered groups {}", vdms.get(NEW).size());
            _logger.info("No of existing vdm discovered groups {}", vdms.get(EXISTING).size());
            if(!vdms.get(NEW).isEmpty()){
                _dbClient.createObject(vdms.get(NEW));
                for(StorageHADomain newVdm:vdms.get(NEW)){
                    _logger.info("New VDM {} ", newVdm.getAdapterName());
                }
            }

            if(!vdms.get(EXISTING).isEmpty()){
                _dbClient.persistObject(vdms.get(EXISTING));
                for(StorageHADomain existingVdm:vdms.get(EXISTING)){
                    _logger.info("Existing VDM {}", existingVdm.getAdapterName());
                }
            }

            // Keep a set of active data movers.  Data movers in 'standby' state are not added to the
            // database since they cannot be used in this state.
            Set<StorageHADomain> activeVDMs = new HashSet<StorageHADomain>();
            List<StorageHADomain> newVdms = vdms.get(NEW);
            for (StorageHADomain vdm : newVdms) {
                _logger.debug("New VDM : {}", vdm.getName());
                activeVDMs.add(vdm);
            }
            List<StorageHADomain> existingVdms = vdms.get(EXISTING);
            for (StorageHADomain vdm : existingVdms) {
                _logger.debug("Existing VDM : {}", vdm.getName());
                activeVDMs.add(vdm);
            }

            //Discover VDM Interfaces
            // Discover ports (data mover interfaces) with the data movers in the active set.
            Map<String, List<StoragePort>> vdmPorts = discoverVdmPorts(storageSystem, activeVDMs);

            _logger.info("No of newly discovered port {}", vdmPorts.get(NEW).size());
            _logger.info("No of existing discovered port {}", vdmPorts.get(EXISTING).size());
            if(!vdmPorts.get(NEW).isEmpty()){
                _dbClient.createObject(vdmPorts.get(NEW));
                for(StoragePort port:vdmPorts.get(NEW)){
                    _logger.debug("New VDM Port : {}", port.getPortName());
                }
            }

            if(!vdmPorts.get(EXISTING).isEmpty()){
                _dbClient.persistObject(vdmPorts.get(EXISTING));
                for(StoragePort port:vdmPorts.get(EXISTING)){
                    _logger.info("EXISTING VDM Port : {}", port.getPortName());
                }
            }
            List<StoragePort> allExistingPorts = new ArrayList<StoragePort>(ports.get(EXISTING));
            allExistingPorts.addAll(vdmPorts.get(EXISTING));
            List<StoragePort> allNewPorts = new ArrayList<StoragePort>(ports.get(NEW));
            allNewPorts.addAll(vdmPorts.get(NEW));
            List<StoragePort> allPorts = new ArrayList<StoragePort>(allExistingPorts);
            allPorts.addAll(allNewPorts);
            List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(allPorts,
                    _dbClient, storageSystemId);
            allExistingPorts.addAll(notVisiblePorts);
            StoragePortAssociationHelper.updatePortAssociations(allNewPorts, _dbClient);
            StoragePortAssociationHelper.updatePortAssociations(allExistingPorts, _dbClient);
            ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVpool(poolsToMatchWithVpool, _dbClient, _coordinator,
                    storageSystemId);

            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for Storage System: %s", 
                    storageSystemId.toString());
        } catch (Exception e) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format("Discovery failed for Storage System: %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw new VNXFileCollectionException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (DatabaseException ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    @Override
    public void scan(AccessProfile arg0) throws BaseCollectionException {
        // TODO Auto-generated method stub
    }


    /**
     * Discover the Control Station for the specified VNX File storage array.  Since the StorageSystem object
     * currently exists, this method updates information in the object.
     *
     * @param system
     * @throws VNXFileCollectionException
     */
    private void discoverControlStation(StorageSystem system) throws VNXFileCollectionException {

        _logger.info("Start Control Station discovery for storage system {}", system.getId());
        VNXControlStation tmpSystem = null;
        try {
            tmpSystem = getControlStation(system);
        } catch (VNXException e) {
            throw new VNXFileCollectionException("Get control station op failed", e);
        }

        if (tmpSystem != null) {
            String sysNativeGuid = NativeGUIDGenerator.generateNativeGuid(DiscoveredDataObject.Type.vnxfile.toString(),
                    tmpSystem.getSerialNumber());
            system.setNativeGuid(sysNativeGuid);
            system.setSerialNumber(tmpSystem.getSerialNumber());
            String firmwareVersion = tmpSystem.getSoftwareVersion();
            String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(Type.valueOf(system.getSystemType()));
            
            // Example version String for VNX looks like 7.1.56-5.
            firmwareVersion = firmwareVersion.replaceAll("-", ".").trim();
            minimumSupportedVersion = minimumSupportedVersion.replaceAll("-", ".");
            system.setFirmwareVersion(firmwareVersion);

            _logger.info("Verifying version details : Minimum Supported Version {} - Discovered VNX Version {}", minimumSupportedVersion, firmwareVersion);
            if(VersionChecker.verifyVersionDetails(minimumSupportedVersion, firmwareVersion) < 0)
            {
                system.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                system.setReachableStatus(false);
                DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, system.getId());
                VNXFileCollectionException vnxe = new VNXFileCollectionException(String.format(" ** This version of VNX File is not supported ** Should be a minimum of %s", minimumSupportedVersion));
                throw vnxe;
            }
            system.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            system.setReachableStatus(true);
        } else {
            _logger.error("Failed to retrieve control station info!");
            system.setReachableStatus(false);
        }

        _logger.info("Control Station discovery for storage system {} complete", system.getId());
    }


    /**
     * Returns the list of storage pools for the specified VNX File storage system.
     *
     * @param system  storage system information including credentials.
     * @return Map of New and Existing known storage pools.
     * @throws VNXFileCollectionException
     */
    private Map<String, List<StoragePool>> discoverStoragePools(StorageSystem system,
                                                                List<StoragePool> poolsToMatchWithVpool,
                                                                StringSet fileSharingProtocols)
            throws VNXFileCollectionException, VNXException {

        Map<String, List<StoragePool>> storagePools = new HashMap<String, List<StoragePool>>();

        List<StoragePool> newPools = new ArrayList<StoragePool>();
        List<StoragePool> existingPools = new ArrayList<StoragePool>();

        _logger.info("Start storage pool discovery for storage system {}", system.getId());
        try {
            List<VNXStoragePool> pools = getStoragePools(system);
            
            for (VNXStoragePool vnxPool : pools) {
                StoragePool pool = null;

                URIQueryResultList results = new URIQueryResultList();
                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, vnxPool.getPoolId(), NativeGUIDGenerator.POOL);
                _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getStoragePoolByNativeGuidConstraint(poolNativeGuid),
                        results);
                Iterator<URI> iter = results.iterator();
                while (iter.hasNext()) {
                    StoragePool tmpPool = _dbClient.queryObject(StoragePool.class, iter.next());

                    if (tmpPool!=null && !tmpPool.getInactive() &&
                    		tmpPool.getStorageDevice().equals(system.getId())) {
                        pool = tmpPool;
                        _logger.info("Found StoragePool {} at {}", pool.getPoolName(), poolNativeGuid);
                        break;
                    }
                }

                if (pool == null) {
                    pool = new StoragePool();
                    pool.setId(URIUtil.createId(StoragePool.class));
                   
                    pool.setLabel(poolNativeGuid);
                    pool.setNativeGuid(poolNativeGuid);
                    pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.toString());
                    pool.setPoolServiceType(PoolServiceType.file.toString());
                    pool.setStorageDevice(system.getId());
                    pool.setProtocols(fileSharingProtocols);
                    pool.setNativeId(vnxPool.getPoolId());
                    pool.setPoolName(vnxPool.getName());
                    
                    // Supported resource type indicates what type of file systems are supported.
                    if("true".equalsIgnoreCase(vnxPool.getVirtualProv())) {
                        pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THICK_ONLY.toString());
                    } else {
                        pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_AND_THICK.toString());
                    }
                    pool.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    _logger.info("Creating new storage pool using NativeGuid : {}", poolNativeGuid);
                    newPools.add(pool);
                } else {
                    //Set protocols if it has changed between discoveries or a upgrade scenario
                    pool.setProtocols(fileSharingProtocols);
                    existingPools.add(pool);
                }

                long size = 0;
                if (vnxPool.getDynamic().equals("true")) {
                    _logger.info("Using auto size for capacity.");
                    size = Long.parseLong(vnxPool.getAutoSize());
                } else {
                    size = Long.parseLong(vnxPool.getSize());
                }
                pool.setTotalCapacity(size * BYTESCONV);

                long used = Long.parseLong(vnxPool.getUsedSize()) * BYTESCONV;
                long free = pool.getTotalCapacity() - used;
                if (0 > free) {
                    free = 0;
                }
                pool.setFreeCapacity(free);
                pool.setSubscribedCapacity(used);
                
                if(ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getCompatibilityStatus(), DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name())
                        ||ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getDiscoveryStatus(), DiscoveryStatus.VISIBLE.name())){
                    poolsToMatchWithVpool.add(pool);
                }
                pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                pool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
            _logger.info("Number of pools found {} : ", storagePools.size());
         } catch (NumberFormatException e) {
            _logger.error("Data Format Exception:  Discovery of storage pools failed for storage system {} for {}",
                    system.getId(), e.getMessage());

            VNXFileCollectionException vnxe =
                    new VNXFileCollectionException("Storage pool discovery data error for storage system "
                            + system.getId());
            vnxe.initCause(e);

            throw vnxe;
        }
        _logger.info("Storage pool discovery for storage system {} complete", system.getId());
        for(StoragePool newPool: newPools){
            _logger.info("New Storage Pool : " + newPool);
            _logger.info("New Storage Pool : {} : {}", newPool.getNativeGuid(), newPool.getId());
        }
        for(StoragePool pool: existingPools){
            _logger.info("Old Storage Pool : " + pool);
            _logger.info("Old Storage Pool : {} : {}", pool.getNativeGuid(), pool.getId());
        }
        //return storagePools;
        storagePools.put(this.NEW, newPools);
        storagePools.put(this.EXISTING, existingPools);
        return storagePools;
    }


    /**
     * Discover the Data Movers (Port Groups) for the specified VNX File storage array.
     *
     * @param system storage system information including credentials.
     * @return Map of New and Existing  port groups
     * @throws VNXFileCollectionException
     */
    private HashMap<String, List<StorageHADomain>> discoverPortGroups(StorageSystem system,
                                                                      StringSet fileSharingProtocols)
            throws VNXFileCollectionException, VNXException {
        HashMap<String, List<StorageHADomain>> portGroups = new HashMap<String, List<StorageHADomain>>();

        List<StorageHADomain> newPortGroups = new ArrayList<StorageHADomain>();
        List<StorageHADomain> existingPortGroups = new ArrayList<StorageHADomain>();
        boolean isNfsCifsSupported = false;

        _logger.info("Start port group discovery for storage system {}", system.getId());


        List<VNXDataMover> dataMovers = getPortGroups(system);
        _logger.debug("Number movers found: {}", dataMovers.size());
        for (VNXDataMover mover : dataMovers) {
                StorageHADomain portGroup = null;

                if ( null == mover ) {
                    _logger.debug("Null data mover in list of port groups.");
                    continue;
                }
                if ( mover.getRole().equals(DM_ROLE_STANDBY) ) {
                    _logger.debug("Found standby data mover");
                    continue;
                }

                // Check if port group was previously discovered
                URIQueryResultList results = new URIQueryResultList();
                String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, mover.getName(), NativeGUIDGenerator.ADAPTER);
                _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                        results);
                Iterator<URI> iter = results.iterator(); 
                while (iter.hasNext()) {
                    StorageHADomain tmpGroup = _dbClient.queryObject(StorageHADomain.class, iter.next());

                    if (tmpGroup!=null && !tmpGroup.getInactive() 
                    		&& tmpGroup.getStorageDeviceURI() .equals(system.getId())) {
                        portGroup = tmpGroup;
                        _logger.debug("Found duplicate {} ", mover.getName());
                    }
                }

                // Check supported network file sharing protocols.
                StringSet protocols = new StringSet();
                protocols.add(StorageProtocol.File.NFS.name());
                protocols.add(StorageProtocol.File.CIFS.name());

                // If the data mover (aka port group) was not previously discovered
                if (portGroup == null) {
                    portGroup = new StorageHADomain();
                    portGroup.setId(URIUtil.createId(StorageHADomain.class));
                    portGroup.setNativeGuid(adapterNativeGuid);
                    portGroup.setStorageDeviceURI(system.getId());
                    portGroup.setAdapterName(mover.getName());
                    portGroup.setName((Integer.toString(mover.getId())));
                    portGroup.setFileSharingProtocols(protocols);
                    _logger.info("Found data mover {} at {}", mover.getName(),  mover.getId());

                    newPortGroups.add(portGroup);
                } else {
                    //Set protocols if it has changed between discoveries or a upgrade scenario
                    portGroup.setFileSharingProtocols(protocols);
                    existingPortGroups.add(portGroup);
                }
                
        }

        // With current API, NFS/CIFS is assumed to be always supported.
        fileSharingProtocols.add(StorageProtocol.File.NFS.name());
        fileSharingProtocols.add(StorageProtocol.File.CIFS.name());

        _logger.info("Port group discovery for storage system {} complete.", system.getId());
        for(StorageHADomain newDomain : newPortGroups){
            _logger.info("New Storage Domain : {} : {}", newDomain.getNativeGuid(), newDomain.getAdapterName() +":" + newDomain.getId());
        }
        for(StorageHADomain domain : existingPortGroups){
            _logger.info("Old Storage Domain : {} : {}", domain.getNativeGuid(), domain.getAdapterName() +":" + domain.getId());
        }
        //return portGroups;
        portGroups.put(NEW, newPortGroups);
        portGroups.put(EXISTING, existingPortGroups);
        return portGroups;
    }


    /**
     * Retrieve the Data Mover IP Interfaces (aka Storage Ports) for the specified VNX File Storage Array
     *
     * @param system storage system information including credentials.
     * @return  Map of New and Existing Storage Ports
     * @throws VNXFileCollectionException
     * @throws IOException 
     */
    private HashMap<String, List<StoragePort>> discoverPorts(StorageSystem system, Set<StorageHADomain> movers)
            throws VNXFileCollectionException, VNXException, IOException  {

        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();

        _logger.info("Start storage port discovery for storage system {}", system.getId());

            // Retrieve the list of data movers interfaces for the VNX File device.
            List<VNXDataMoverIntf> allDmIntfs = getPorts(system);

            List<VNXVdm> vdms = getVdmPortGroups(system);

            //Filter VDM ports
            List<VNXDataMoverIntf> dataMovers = null;
            Map<String,VNXDataMoverIntf> dmIntMap = new HashMap();

            for(VNXDataMoverIntf intf:allDmIntfs){
                _logger.info("getPorts Adding {} : {}", intf.getName(), intf.getIpAddress());
                dmIntMap.put(intf.getName(), intf);
            }

            // Changes to fix Jira CTRL - 9151
            VNXFileSshApi sshDmApi = new VNXFileSshApi();
            sshDmApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());
            
            //collect VDM interfaces
            for(VNXVdm vdm:vdms){
                //Sometimes getVdmPortGroups(system) method does not collect all VDM interfaces,
            	//So running Collect NFS/CIFS interfaces from nas_server -info command. This will return
                //Interfaces assigned to VDM and not thru CIFS servers
                Map<String, String> vdmIntfs = sshDmApi.getVDMInterfaces(vdm.getVdmName());
                for(String vdmIF:vdmIntfs.keySet()){
                    _logger.info("Remove VDM interface {}", vdmIF);
                    dmIntMap.remove(vdmIF);
                }
            }

            //Got the filtered out DataMover Interfaces
            List<VNXDataMoverIntf> dmIntfs = new ArrayList(dmIntMap.values());

            _logger.info("Number unfiltered mover interfaces found: {}", allDmIntfs.size());
            _logger.info("Number mover interfaces found: {}", dmIntfs.size());

            // Create the list of storage ports.
            for (VNXDataMoverIntf intf : dmIntfs) {
                StoragePort port = null;

                StorageHADomain matchingHADomain = getMatchingMoverById(movers, intf.getDataMoverId()); 
                // Check for valid data mover
                if (null == matchingHADomain) {
                    continue;
                }

                // Check if storage port was already discovered
                String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, intf.getIpAddress(), NativeGUIDGenerator.PORT);
                
                port = findExistingPort(portNativeGuid);
                if (null == port) {
                    // Since a port was not found, attempt with previous naming convention (ADAPTER instead of PORT)
                    String oldNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            system, intf.getIpAddress(), NativeGUIDGenerator.ADAPTER);
                    
                    port = findExistingPort(oldNativeGuid);
                    if (null != port) {
                        // found with old naming convention, therefore update name.
                        port.setLabel(portNativeGuid);
                        port.setNativeGuid(portNativeGuid);
                    }
                }                

                // If data mover interface was not previously discovered, add new storage port
                if (port == null) {
                    port = new StoragePort();
                    port.setId(URIUtil.createId(StoragePort.class));
                    port.setLabel(portNativeGuid);
                    port.setTransportType("IP");
                    port.setNativeGuid(portNativeGuid);
                    port.setStorageDevice(system.getId());
                    port.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    port.setPortName(intf.getName());
                    port.setPortNetworkId(intf.getIpAddress());
                    port.setPortGroup(intf.getDataMoverId());
                    port.setStorageHADomain(matchingHADomain.getId());
                    _logger.info(
                            "Creating new storage port using NativeGuid : {} name : {}, IP : {}",
                            new Object[] { portNativeGuid, intf.getName(),
                                    intf.getIpAddress() });
                    newStoragePorts.add(port);
                } else {
                    port.setStorageHADomain(matchingHADomain.getId());
                    existingStoragePorts.add(port);
                }
                port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());                
            }
            

        _logger.info("Storage port discovery for storage system {} complete", system.getId());
        for(StoragePort newPort: newStoragePorts){
            _logger.info("New Storage Port : {} : {}", newPort.getNativeGuid(), newPort.getPortName() +":" + newPort.getId());
        }
        for(StoragePort port: existingStoragePorts){
            _logger.info("Old Storage Port : {} : {}", port.getNativeGuid(), port.getPortName() +":" + port.getId());
        }
        storagePorts.put(NEW, newStoragePorts);
        storagePorts.put(EXISTING, existingStoragePorts);
        return storagePorts;
    }
   
    /**
     * Discover the Data Movers (Port Groups) for the specified VNX File storage array.
     *
     * @param system storage system information including credentials.
     * @param movers Collection of all DataMovers in the VNX File storage array
     * @return Map of New and Existing  VDM port groups
     * @throws VNXFileCollectionException
     */
    private HashMap<String, List<StorageHADomain>> discoverVdmPortGroups(StorageSystem system, Set<StorageHADomain> movers)
            throws VNXFileCollectionException, VNXException {
        HashMap<String, List<StorageHADomain>> portGroups = new HashMap();

        List<StorageHADomain> newPortGroups = new ArrayList<StorageHADomain>();
        List<StorageHADomain> existingPortGroups = new ArrayList<StorageHADomain>();

        _logger.info("Start vdm port group discovery for storage system {}", system.getId());

            List<VNXVdm> vdms = getVdmPortGroups(system);
            _logger.debug("Number VDM found: {}", vdms.size());
            VNXFileSshApi sshDmApi = new VNXFileSshApi();
            sshDmApi.setConnParams(system.getIpAddress(), system.getUsername(),
                    system.getPassword());
            for (VNXVdm vdm : vdms) {
                StorageHADomain portGroup = null;
                // Check supported network file sharing protocols.
                StringSet protocols = new StringSet();

                if ( null == vdm ) {
                    _logger.debug("Null vdm in list of port groups.");
                    continue;
                }

                // Check if port group was previously discovered
                URIQueryResultList results = new URIQueryResultList();
                String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, vdm.getVdmName(), NativeGUIDGenerator.ADAPTER);
                _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                        results);
                Iterator<URI> iter = results.iterator();
                while (iter.hasNext()) {
                    StorageHADomain tmpGroup = _dbClient.queryObject(StorageHADomain.class, iter.next());

                    if (tmpGroup!=null && !tmpGroup.getInactive()
                    		&& tmpGroup.getStorageDeviceURI() .equals(system.getId())) {
                        portGroup = tmpGroup;
                        _logger.debug("Found duplicate {} ", vdm.getVdmName());
                        break;
                    }
                }

                Map<String, String> vdmIntfs = sshDmApi.getVDMInterfaces(vdm.getVdmName());
                Set<String> intfs = vdmIntfs.keySet();
                //if NFS Interfaces are not there ignore this..
                if(vdmIntfs == null || intfs.isEmpty()) {
                    //There are no interfaces for this VDM via nas_server command
                    //so ignore this
                    _logger.info("Ignoring VDM {} because no NFS interfaces found via ssh query", vdm.getVdmName());
                } else {
                    _logger.info("Process VDM {} because  interfaces found {}", vdm.getVdmName(), vdmIntfs.keySet().size());
                }

                for (String intf: intfs) {
                    String vdmCapability = vdmIntfs.get(intf);
                    _logger.info("Interface {} capability [{}]", vdm.getVdmName() + ":" + intf, vdmCapability);
                    if(vdmCapability.contains("cifs")){
                        _logger.info("{} has CIFS Enabled since interfaces are found ", vdm.getVdmName(), intf+":"+vdmCapability);
                        protocols.add(StorageProtocol.File.CIFS.name());
                    }
                    if(vdmCapability.contains("vdm")){
                        _logger.info("{} has NFS Enabled since interfaces are found ", vdm.getVdmName(),  intf+":"+vdmCapability);
                        protocols.add(StorageProtocol.File.NFS.name());
                    }
                }

                List<VNXCifsServer> cifsServers = getCifServers(system, vdm.getVdmId(), "true");

                for(VNXCifsServer cifsServer:cifsServers){
                    _logger.info("Cifs Server {} for {} ", cifsServer.getName(), vdm.getVdmName());
                    if(!cifsServer.getInterfaces().isEmpty()) {
                        _logger.info("{} has CIFS Enabled since interfaces are found ", vdm.getVdmName(),
                                cifsServer.getName()  + ":" + cifsServer.getInterfaces());
                        protocols.add(StorageProtocol.File.CIFS.name());
                    }
                }

                if(protocols.isEmpty()) {
                    //No valid interfaces found and ignore this
                    _logger.info("Ignoring VDM {} because no NFS/CIFS interfaces found ", vdm.getVdmName());
                    continue;
                }

                // If the data mover (aka port group) was not previously discovered
                if (portGroup == null) {
                    portGroup = new StorageHADomain();
                    portGroup.setId(URIUtil.createId(StorageHADomain.class));
                    portGroup.setNativeGuid(adapterNativeGuid);
                    portGroup.setStorageDeviceURI(system.getId());
                    portGroup.setAdapterName(vdm.getVdmName());
                    portGroup.setName(vdm.getVdmId());
                    portGroup.setFileSharingProtocols(protocols);
                    portGroup.setVirtual(true);
                    portGroup.setAdapterType(StorageHADomain.HADomainType.VIRTUAL.toString());

                    //Get parent Data Mover
                    StorageHADomain matchingParentMover = getMatchingMoverById(movers, vdm.getMoverId());
                    // Check for valid data mover
                    if (null != matchingParentMover) {
                        portGroup.setParentHADomainURI(matchingParentMover.getId());
                    } else {
                        _logger.info("Matching parent DataMover {} for {} not found ", vdm.getMoverId(), vdm.getVdmName());
                    }
                    _logger.info("Found Vdm {} at {}", vdm.getVdmName(),  vdm.getVdmId()
                            +"@"+vdm.getMoverId());
                    newPortGroups.add(portGroup);
                } else {
                    //For rediscovery if cifs is not enabled
                    portGroup.setFileSharingProtocols(protocols);
                    existingPortGroups.add(portGroup);
                }
        }

        _logger.info("Vdm Port group discovery for storage system {} complete.", system.getId());
        for(StorageHADomain newDomain : newPortGroups){
            _logger.debug("New Storage Domain : {} : {}", newDomain.getNativeGuid(), newDomain.getAdapterName() + ":" + newDomain.getId());
        }
        for(StorageHADomain domain : existingPortGroups){
            _logger.debug("Old Storage Domain : {} : {}", domain.getNativeGuid(), domain.getAdapterName() + ":" + domain.getId());
        }
        //return portGroups;
        portGroups.put(NEW, newPortGroups);
        portGroups.put(EXISTING, existingPortGroups);
        return portGroups;
    }

    /**
     * Retrieve the Data Mover IP Interfaces (aka Storage Ports) for the specified VNX File Storage Array
     *
     * @param system storage system information including credentials.
     * @return  Map of New and Existing Storage Ports
     * @throws VNXFileCollectionException
     * @throws IOException
     */
    private HashMap<String, List<StoragePort>> discoverVdmPorts(StorageSystem system, Set<StorageHADomain> movers)
            throws VNXFileCollectionException, VNXException, IOException  {

        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();

        _logger.info("Start storage port discovery for storage system {}", system.getId());

        HashMap<String, VNXDataMoverIntf> vdmIntMap = new HashMap();

            // Retrieve VDMs
        List<VNXVdm> vdms = getVdmPortGroups(system);

        // Retrieve the list of data movers interfaces for the VNX File device.
        List<VNXDataMoverIntf> vdmIntfs = getVdmPorts(system, vdms);

        for(VNXDataMoverIntf intf:vdmIntfs){
                _logger.info("getVdmPorts Adding {} : {}", intf.getName(), intf.getIpAddress());
                vdmIntMap.put(intf.getName(), intf);
        }

        _logger.info("Number VDM mover interfaces found: {}", vdmIntfs.size());

        for(VNXVdm vdm:vdms) {

                // Create the list of storage ports.
                for (String vdmIF:vdm.getInterfaces()) {

                    VNXDataMoverIntf intf = vdmIntMap.get(vdmIF);

                    StoragePort port = null;

                    StorageHADomain matchingHADomain = getMatchingMoverByName(movers, vdm.getVdmName());
                    // Check for valid data mover
                    if (null == matchingHADomain) {
                        continue;
                    }

                    // Check if storage port was already discovered
                    String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            system, intf.getIpAddress(), NativeGUIDGenerator.PORT);

                    port = findExistingPort(portNativeGuid);

                    // If VDM interface was not previously discovered, add new storage port
                    if (port == null) {
                        port = new StoragePort();
                        port.setId(URIUtil.createId(StoragePort.class));
                        port.setLabel(portNativeGuid);
                        port.setTransportType("IP");
                        port.setNativeGuid(portNativeGuid);
                        port.setStorageDevice(system.getId());
                        port.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                        port.setPortName(intf.getName());
                        port.setPortNetworkId(intf.getIpAddress());
                        port.setPortGroup(vdm.getVdmId());
                        port.setStorageHADomain(matchingHADomain.getId());
                        _logger.info(
                                "Creating new storage port using NativeGuid : {} name : {}, IP : {}",
                                new Object[] { portNativeGuid, intf.getName(),
                                        intf.getIpAddress(), intf.getDataMoverId(), vdm.getVdmId(), port.getPortName(), port.getPortGroup() });
                        newStoragePorts.add(port);
                    } else {
                        port.setStorageHADomain(matchingHADomain.getId());
                        port.setPortGroup(vdm.getVdmId());
                        existingStoragePorts.add(port);
                    }
                    port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                    port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                }
        }

        _logger.info("Storage port discovery for storage system {} complete", system.getId());
        for(StoragePort newPort: newStoragePorts){
            _logger.debug("New Storage Port : {} : {}", newPort.getNativeGuid(), newPort.getPortName() + ":" + newPort.getId());
        }
        for(StoragePort port: existingStoragePorts){
            _logger.debug("Old Storage Port : {} : {}", port.getNativeGuid(), port.getPortName() + ":" + port.getId());
        }
        storagePorts.put(NEW, newStoragePorts);
        storagePorts.put(EXISTING, existingStoragePorts);
        
        return storagePorts;
    }

    private StorageHADomain getMatchingMoverByName(Set<StorageHADomain> movers, String moverName) {
        for (StorageHADomain mover: movers) {
            if(mover.getAdapterName().equals(moverName)) {
                return mover;
            }
        }
        return null;
    }

    private StorageHADomain getMatchingMoverById(Set<StorageHADomain> movers, String moverId) {
        for (StorageHADomain mover: movers) {
            if(mover.getName().equals(moverId)) {
                return mover;
            }
        }
        return null;
    }

    private StoragePort findExistingPort(String portGuid) {
        URIQueryResultList results = new URIQueryResultList();
        StoragePort port = null;

        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(portGuid),
                results);
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            StoragePort tmpPort = _dbClient.queryObject(StoragePort.class, iter.next());

            if (tmpPort!=null && !tmpPort.getInactive()){
                port = tmpPort;
                _logger.info("found port {}", tmpPort.getNativeGuid() + ":" + tmpPort.getPortName());
                break;
            }
        }
        return port;
    }

    private StoragePort findExistingPort(String portGuid, StorageSystem system, VNXDataMoverIntf intf) {
        URIQueryResultList results = new URIQueryResultList();
        StoragePort port = null;
        
        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(portGuid),
                results);
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            _logger.info("cross verifying for duplicate port");
            
            StoragePort tmpPort = _dbClient.queryObject(StoragePort.class, iter.next());

            _logger.info(
                    "StorageDevice found for port {} - Actual StorageDevice {} : PortGroup found for port {} - Actual PortGroup {}",
                    new Object[]{tmpPort.getStorageDevice(), system.getId(), tmpPort.getPortGroup(), intf.getDataMoverId()});

            if (tmpPort!=null && !tmpPort.getInactive() 
            		&& tmpPort.getStorageDevice().equals(system.getId()) &&
                    tmpPort.getPortGroup().equals(intf.getDataMoverId())) {
                port = tmpPort;
                _logger.info("found duplicate dm intf {}", intf.getName());
                break;
            }
        }
        
        return port;
    }

    private StoragePort findExistingPort(String portGuid, StorageSystem system, String moverId) {
        URIQueryResultList results = new URIQueryResultList();
        StoragePort port = null;

        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(portGuid),
                results);
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            _logger.info("cross verifying for duplicate port");

            StoragePort tmpPort = _dbClient.queryObject(StoragePort.class, iter.next());

            _logger.info(
                    "StorageDevice found for port {} - Actual StorageDevice {} : PortGroup found for port {} - Actual PortGroup {}",
                    new Object[]{tmpPort.getStorageDevice(), system.getId(), tmpPort.getPortGroup(), moverId});

            if (tmpPort!=null && !tmpPort.getInactive() && tmpPort.getStorageDevice().equals(system.getId()) &&
                    tmpPort.getPortGroup().equals(moverId)) {
                port = tmpPort;
                _logger.info("found duplicate dm intf {}", moverId);
                break;
            }
        }

        return port;
    }

    
    private void discoverUmanagedFileSystems(AccessProfile profile)  throws BaseCollectionException {

        _logger.info("Access Profile Details :  IpAddress : PortNumber : {}, namespace : {}",
                profile.getIpAddress() + profile.getPortNumber(),
                profile.getnamespace());

        URI storageSystemId = profile.getSystemId();

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if(null == storageSystem){
            return;
        }

        List<UnManagedFileSystem> unManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        List<UnManagedFileSystem> existingUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        int newFileSystemsCount = 0;
        int existingFileSystemsCount = 0;
        Set<URI> allDiscoveredUnManagedFileSystems = new HashSet<URI>();
        
        String detailedStatusMessage = "Discovery of VNXFile Unmanaged FileSystem started";

        try {
            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                    storagePoolURIs);

            HashMap<String, StoragePool> pools = new HashMap();
            Iterator<URI> poolsItr = storagePoolURIs.iterator();
            while (poolsItr.hasNext()) {
                URI storagePoolURI = poolsItr.next();
                StoragePool storagePool = _dbClient.queryObject(StoragePool.class, storagePoolURI);
                if(storagePool!=null && !storagePool.getInactive()){
                	pools.put(storagePool.getNativeId(), storagePool);
                }
            }

            StoragePort storagePort = this.getStoragePortPool(storageSystem);

            List<VNXFileSystem> discoveredFS = discoverAllFileSystems(storageSystem);
            if(discoveredFS != null) {
                for(VNXFileSystem fs: discoveredFS){
                    String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            storageSystem.getSystemType(),
                            storageSystem.getSerialNumber(), fs.getFsId()+"");
                    StoragePool pool = pools.get(fs.getStoragePool());
                   
                    if(!checkStorageFileSystemExistsInDB(fsNativeGuid)){
                        //Create UnManaged FS
                    	 String fsUnManagedFsNativeGuid =
                                 NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(storageSystem.getSystemType(),
                                         storageSystem.getSerialNumber().toUpperCase(), fs.getFsId()+"");

                        UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                        
                        boolean alreadyExist = unManagedFs == null ? false : true;
                        unManagedFs = createUnManagedFileSystem(unManagedFs, fsUnManagedFsNativeGuid, storageSystem,
                                pool, storagePort, fs);
                        if(alreadyExist) {
                            existingUnManagedFileSystems.add(unManagedFs);
                            existingFileSystemsCount++;
                        } else {
                            unManagedFileSystems.add(unManagedFs);
                            newFileSystemsCount++;
                        }
                        
                        allDiscoveredUnManagedFileSystems.add(unManagedFs.getId());
                        /**
                         * Persist 200 objects and clear them to avoid memory issue
                         */
                        validateListSizeLimitAndPersist(unManagedFileSystems, existingUnManagedFileSystems, 
                        		Constants.DEFAULT_PARTITION_SIZE * 2);
                    }

                }
            }
            
            // Process those active unmanaged fs objects available in database but not in newly discovered items, to mark them inactive.
            markUnManagedFSObjectsInActive(storageSystem, allDiscoveredUnManagedFileSystems);
            _logger.info("New unmanaged VNXFile file systems count: {}", newFileSystemsCount);
            _logger.info("Update unmanaged VNXFile file systems count: {}", existingFileSystemsCount);
            if(!unManagedFileSystems.isEmpty()) {
                //Add UnManagedFileSystem
                _dbClient.createObject(unManagedFileSystems);
            }

            if(!existingUnManagedFileSystems.isEmpty()) {
                //Update UnManagedFilesystem
                _dbClient.persistObject(existingUnManagedFileSystems);
            }

            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for VNXFile: %s",
                    storageSystemId.toString());
        } catch (Exception e) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format("Discovery failed for VNXFile %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw new VNXFileCollectionException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }
    
    private void validateListSizeLimitAndPersist(List<UnManagedFileSystem> newUnManagedFileSystems,
    		List<UnManagedFileSystem> existingUnManagedFileSystems, int limit){

    	if(newUnManagedFileSystems!=null && !newUnManagedFileSystems.isEmpty() && newUnManagedFileSystems.size()>= limit){
            _dbClient.createObject(newUnManagedFileSystems);
    		newUnManagedFileSystems.clear();
    	}

    	if(existingUnManagedFileSystems!=null && !existingUnManagedFileSystems.isEmpty() && existingUnManagedFileSystems.size()>= limit){
            _dbClient.persistObject(existingUnManagedFileSystems);
    		existingUnManagedFileSystems.clear();
    	}
    }

    private void discoverUnmanagedExports(AccessProfile profile) {
        
        // Get Storage System
        URI storageSystemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if(null == storageSystem){
            return;
        }

        String detailedStatusMessage = "Discovery of VNX Unmanaged Exports started";
        
        try {
            
            // Discover port groups (data mover ids) and group names (data mover names)
            Set <StorageHADomain>  activeDataMovers = discoverActiveDataMovers(storageSystem);

            // Reused from discoverAll
            // Discover ports (data mover interfaces) with the data movers in the active set.
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem, activeDataMovers);

            _logger.info("No of newly discovered port {}", ports.get(NEW).size());
            _logger.info("No of existing discovered port {}", ports.get(EXISTING).size());
            if(!ports.get(NEW).isEmpty()){
                _dbClient.createObject(ports.get(NEW));
            }
            
            List<StoragePort> allPortsList = ports.get(NEW);
            allPortsList.addAll(ports.get(EXISTING));

            Map<String, List<StoragePort>> allPorts = new ConcurrentHashMap<String, List<StoragePort>>();
            for (StoragePort sPort: allPortsList) {
                _logger.debug("DM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                List<StoragePort>  spList = allPorts.get(sPort.getStorageHADomain().toString());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);
                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }

            Map<String, List<StorageHADomain>> allVdms = discoverVdmPortGroups(storageSystem, activeDataMovers);
            if(!allVdms.get(NEW).isEmpty()){
                _dbClient.createObject(allVdms.get(NEW));
            }

            Set<StorageHADomain> allActiveVDMs = new HashSet();
            allActiveVDMs.addAll(allVdms.get(NEW));
            allActiveVDMs.addAll(allVdms.get(EXISTING));

            activeDataMovers.addAll(allVdms.get(NEW));
            activeDataMovers.addAll(allVdms.get(EXISTING));


            Map<String, List<StoragePort>> allVdmPorts =  discoverVdmPorts(storageSystem, allActiveVDMs);
            if(!allVdmPorts.get(NEW).isEmpty()){
                _dbClient.createObject(allVdmPorts.get(NEW));
            }

            List<StoragePort> allVDMPortsList = allVdmPorts.get(NEW);
            allVDMPortsList.addAll(allVdmPorts.get(EXISTING));

            for (StoragePort sPort: allVDMPortsList) {
                List<StoragePort>  spList = allPorts.get(sPort.getStorageHADomain().toString());
                _logger.debug("VDM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);
                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }
            
            List<UnManagedFileSystem> unManagedExportBatch = new ArrayList<>();

            List<StorageHADomain> moversAndVDMs = new ArrayList<>();
            moversAndVDMs.addAll(activeDataMovers);
            moversAndVDMs.addAll(allActiveVDMs);
            
            for(StorageHADomain mover: moversAndVDMs) {

                _logger.info("Processing DataMover/VDM {} {}", mover.getId(), mover.getAdapterName());

                // Get storage port and name for the DM
                if(allPorts.get(mover.getId().toString()) == null || allPorts.get(mover.getId().toString()).isEmpty()) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.info("No Ports found for {} {}", mover.getName(), mover.getAdapterName());
                    continue;
                }  else {
                    _logger.info("Number of  Ports found for {} : {} ", mover.getName() +":" + mover.getAdapterName(),
                            allPorts.get(mover.getId().toString()).size());
                }
                Collections.shuffle(allPorts.get(mover.getId().toString()));
                StoragePort storagePort = allPorts.get(mover.getId().toString()).get(0);
                if (storagePort == null) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.info("StoragePort is null");
                    continue;
                }
                //storagePort.setStorageHADomain(mover.getId());

                // Retrieve FS-mountpath map for the Data Mover.
                _logger.info("Retrieving FS-mountpath map for Data Mover {}.", 
                        mover.getAdapterName());
                VNXFileSshApi sshDmApi = new VNXFileSshApi();
                sshDmApi.setConnParams(storageSystem.getIpAddress(), storageSystem.getUsername(), 
                        storageSystem.getPassword());

                Map<String, String> fileSystemMountpathMap = sshDmApi.getFsMountpathMap(
                        mover.getAdapterName());

                Map <String, Map<String, String>> moverExportDetails =
                        sshDmApi.getNFSExportsForPath(mover.getAdapterName());

                Map<String, String> nameIdMap = getFsNameFsNativeIdMap(storageSystem);

                // Loop through the map and, if the file exists in DB, retrieve the 
                // export, process export, and associate export with the FS                    
                Set<String> fsNames = fileSystemMountpathMap.keySet();
                for (String fsName: fsNames) {
                    // Retrieve FS from DB.  If FS found, retrieve export and process
                    String fsMountPath = fileSystemMountpathMap.get(fsName);
                    
                    // Get FS ID for nativeGUID
                    //VNXFileSystem vnxFileSystems = discoverNamedFileSystem(storageSystem, fsName);
                    String fsId = nameIdMap.get(fsName);
                    _logger.info("Resolved FileSystem name {} to native Id : Path {}", fsName, fsId + ":" + fsMountPath);
                    
                    UnManagedFileSystem vnxufs = null;
                    if (fsId != null){
                        String fsNativeGuid = NativeGUIDGenerator.
                                generateNativeGuidForPreExistingFileSystem(
                                        storageSystem.getSystemType(), storageSystem
                                        .getSerialNumber().toUpperCase(),
                                        fsId);

                        vnxufs = checkUnManagedFileSystemExistsInDB(fsNativeGuid);
                    }
      
                    if (vnxufs != null) {
                        // Get export info
                        int noOfExports = 0;
                        for(String expPath:moverExportDetails.keySet()){
                            if(!expPath.contains(fsMountPath)) {
                                //Ingore this path as it is not among the exports
                                continue;
                            } else {
                                _logger.info("Path : {} " , expPath);
                                noOfExports++;
                            }
                            Map<String, String> fsExportInfo = moverExportDetails.get(expPath);
                            if ((fsExportInfo != null) && (fsExportInfo.size() > 0)) {
                               // If multiple security flavors, do not add to ViPR DB
                               String securityFlavors = fsExportInfo.get(VNXFileConstants.SECURITY_TYPE);
                               if(securityFlavors == null || securityFlavors.length() == 0) {
                                   securityFlavors = "sys";
                               }
                               if (securityFlavors != null) {
                                   String[] securityFlavorArr = securityFlavors.split(
                                           VNXFileConstants.SECURITY_SEPARATORS);
                                   if (securityFlavorArr.length > 1) {
                                       _logger.info("FileSystem "
                                               + fsMountPath
                                               + " has a complex export with multiple security flavors, hence ignoring the filesystem and NOT bringing into ViPR DB");
                                       vnxufs.setInactive(true);
                                   } else {
                                       _logger.info("FileSystem "
                                               + fsMountPath
                                               + " storage port :" + storagePort
                                               + " has a valid export with single security flavors {}, hence processing the filesystem and  bringing into ViPR DB", securityFlavors);
                                       vnxufs.setInactive(false);
                                       associateExportWithFS(vnxufs, expPath, fsExportInfo, fsMountPath,
                                               storagePort);
                                   }
                                }
                            }
                        }
                        _logger.info("No of exports found for path {} = {} ", fsMountPath, noOfExports);

                        if(noOfExports == 0) {
                            _logger.info("FileSystem "
                                    + fsMountPath
                                    + " does not have valid ViPR exports, hence this filesystem cannot be brought into ViPR DB");
                            vnxufs.putFileSystemCharacterstics(UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_INGESTABLE.toString(), FALSE);
                            vnxufs.putFileSystemCharacterstics(UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED.toString(), FALSE);
                            vnxufs.setInactive(true);
                        }

                        _logger.info("UnManaged File System {} valid or invalid {}",
                                vnxufs.getLabel(), vnxufs.getInactive());
                        
                        unManagedExportBatch.add(vnxufs);
                        
                        if(unManagedExportBatch.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
                            _logger.info("Updating {} UnManagedFileSystem in db", unManagedExportBatch.size());
                            //Add UnManagedFileSystem batch
                            _partitionManager.updateInBatches(unManagedExportBatch,
                                    Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                                    UNMANAGED_FILESYSTEM);
                            unManagedExportBatch.clear();

                        }
                    }
                }
            }

            
            if (!unManagedExportBatch.isEmpty()) {
                _logger.info("Updating {} UnManagedFileSystem in db", unManagedExportBatch.size());
                // Update UnManagedFilesystem
                _partitionManager.updateInBatches(unManagedExportBatch,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                        UNMANAGED_FILESYSTEM);
                unManagedExportBatch.clear();
            }
            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for VNXFile export: %s",
                    storageSystemId.toString());

        } catch (Exception ex) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format("Discovery failed for VNXFile exports %s because %s",
                    storageSystemId.toString(), ex.getLocalizedMessage());
            _logger.error(detailedStatusMessage, ex);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
        
    }
    
    private void discoverUnManagedCifsShares(AccessProfile profile) {
        
        // Get Storage System
        URI storageSystemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if(null == storageSystem){
            return;
        }

        String detailedStatusMessage = "Discovery of VNX Unmanaged Shares started";
        _logger.info(detailedStatusMessage);
        
		// Used to Save the CIFS ACLs to DB
		List<UnManagedCifsShareACL> newUnManagedCifsACLs = new ArrayList<UnManagedCifsShareACL>();
        List<UnManagedCifsShareACL> oldUnManagedCifsACLs = new ArrayList<UnManagedCifsShareACL>();


        try {
            
            // Discover port groups (data mover ids) and group names (data mover names)
            Set <StorageHADomain>  activeDataMovers = discoverActiveDataMovers(storageSystem);

            // Reused from discoverAll
            // Discover ports (data mover interfaces) with the data movers in the active set.
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem, activeDataMovers);

            _logger.info("No of newly discovered port {}", ports.get(NEW).size());
            _logger.info("No of existing discovered port {}", ports.get(EXISTING).size());
            if(!ports.get(NEW).isEmpty()){
                _dbClient.createObject(ports.get(NEW));
            }
            
            List<StoragePort> allPortsList = ports.get(NEW);
            allPortsList.addAll(ports.get(EXISTING));

            Map<String, List<StoragePort>> allPorts = new ConcurrentHashMap<String, List<StoragePort>>();
            for (StoragePort sPort: allPortsList) {
                _logger.debug("DM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                List<StoragePort>  spList = allPorts.get(sPort.getStorageHADomain().toString());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);
                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }

            Map<String, List<StorageHADomain>> allVdms = discoverVdmPortGroups(storageSystem, activeDataMovers);
            if(!allVdms.get(NEW).isEmpty()){
                _dbClient.createObject(allVdms.get(NEW));
            }

            Set<StorageHADomain> allActiveVDMs = new HashSet();
            allActiveVDMs.addAll(allVdms.get(NEW));
            allActiveVDMs.addAll(allVdms.get(EXISTING));

            activeDataMovers.addAll(allVdms.get(NEW));
            activeDataMovers.addAll(allVdms.get(EXISTING));


            Map<String, List<StoragePort>> allVdmPorts =  discoverVdmPorts(storageSystem, allActiveVDMs);
            if(!allVdmPorts.get(NEW).isEmpty()){
                _dbClient.createObject(allVdmPorts.get(NEW));
            }

            List<StoragePort> allVDMPortsList = allVdmPorts.get(NEW);
            allVDMPortsList.addAll(allVdmPorts.get(EXISTING));

            for (StoragePort sPort: allVDMPortsList) {
                List<StoragePort>  spList = allPorts.get(sPort.getStorageHADomain().toString());
                _logger.debug("VDM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);
                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }
            
            List<UnManagedFileSystem> unManagedExportBatch = new ArrayList<UnManagedFileSystem>();
  
            for(StorageHADomain mover: activeDataMovers) {

                // Get storage port and name for the DM
                if(allPorts.get(mover.getId().toString()) == null || allPorts.get(mover.getId().toString()).isEmpty()) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.debug("No Ports found for {} {}", mover.getName(), mover.getAdapterName());
                    continue;
                }  else {
                    _logger.debug("Number of  Ports found for {} : {} ", mover.getName() +":" + mover.getAdapterName(),
                            allPorts.get(mover.getId().toString()).size());
                }
                Collections.shuffle(allPorts.get(mover.getId().toString()));
                StoragePort storagePort = allPorts.get(mover.getId().toString()).get(0);
                if (storagePort == null) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.debug("StoragePort is null");
                    continue;
                }
                //storagePort.setStorageHADomain(mover.getId());

                // Retrieve FS-mountpath map for the Data Mover.
                _logger.info("Retrieving FS-mountpath map for Data Mover {}.", 
                        mover.getAdapterName());
                VNXFileSshApi sshDmApi = new VNXFileSshApi();
                sshDmApi.setConnParams(storageSystem.getIpAddress(), storageSystem.getUsername(), 
                        storageSystem.getPassword());

                Map<String, String> fileSystemMountpathMap = sshDmApi.getFsMountpathMap(
                        mover.getAdapterName());

                Map <String, Map<String, String>> moverExportDetails =
                        sshDmApi.getCIFSExportsForPath(mover.getAdapterName());

                Map<String, String> nameIdMap = getFsNameFsNativeIdMap(storageSystem);

                // Loop through the map and, if the file exists in DB, retrieve the 
                // export, process export, and associate export with the FS                    
                Set<String> fsNames = fileSystemMountpathMap.keySet();
                for (String fsName: fsNames) {
                    // Retrieve FS from DB.  If FS found, retrieve export and process
                    String fsMountPath = fileSystemMountpathMap.get(fsName);
                    
                    // Get FS ID for nativeGUID
                    //VNXFileSystem vnxFileSystems = discoverNamedFileSystem(storageSystem, fsName);
                    String fsId = nameIdMap.get(fsName);
                    _logger.debug("Resolved FileSystem name {} to native Id {}",
                    		fsName, fsId);
                    
                    UnManagedFileSystem vnxufs = null;
                    if (fsId != null){
                        String fsNativeGuid = NativeGUIDGenerator.
                                generateNativeGuidForPreExistingFileSystem(
                                        storageSystem.getSystemType(), storageSystem
                                        .getSerialNumber().toUpperCase(),
                                        fsId);

                        vnxufs = checkUnManagedFileSystemExistsInDB(fsNativeGuid);
                    }

                    if (vnxufs != null) {
                    	int noOfShares = 0;
                        // Get export info
                        for(String expPath:moverExportDetails.keySet()){
                            if(!expPath.contains(fsMountPath)) {
                                //Ignore this path as it is not among the exports
                                continue;
                            } else {
                            	// We should process only FS and its sub-directory exports only.
                            	String subDir = expPath.substring(fsMountPath.length());
                            	if(!subDir.isEmpty() && !subDir.startsWith("/")) {
                            		continue;
                            	}
                                _logger.info("Path : {} " , expPath);
                            }
                            Map<String, String> fsExportInfo = moverExportDetails.get(expPath);
                            if ((fsExportInfo != null) && (fsExportInfo.size() > 0)) { 
                            	   noOfShares += 1;
                            	   _logger.info("Associating FS share map for VNX UMFS {}",
                            			   vnxufs.getLabel());
                            	   
                            	   associateCifsExportWithFS(vnxufs, expPath, fsExportInfo,
                                           storagePort);
                            	   vnxufs.setHasShares(true);
                            	   
                            	   _logger.debug("Export map for VNX UMFS {} = {}",
                            			   vnxufs.getLabel(), vnxufs.getUnManagedSmbShareMap());
                            	                               	   
                            	   List<UnManagedCifsShareACL> cifsACLs = 
                            			   applyCifsSecurityRules(vnxufs, expPath, fsExportInfo, storagePort);
                            	   
                                    _logger.info("Number of acls discovered for file system {} is {}",
                                    		vnxufs.getId() + ":" + vnxufs.getLabel(), cifsACLs.size());

                                    for (UnManagedCifsShareACL cifsAcl : cifsACLs) {
                                    	
                                        _logger.info("Unmanaged File share acl: {}", cifsAcl);
                                        String fsShareNativeId = cifsAcl.getFileSystemShareACLIndex();
                                        _logger.info("UMFS Share ACL index: {}", fsShareNativeId);
                                        String fsUnManagedFileShareNativeGuid = NativeGUIDGenerator
                                                .generateNativeGuidForPreExistingFileShare(
                                                        storageSystem, fsShareNativeId);
                                        _logger.info("Native GUID {}", fsUnManagedFileShareNativeGuid);
                                        cifsAcl.setNativeGuid(fsUnManagedFileShareNativeGuid);
                                        
                                        // Check whether the CIFS share ACL was present in ViPR DB.
                                        UnManagedCifsShareACL existingACL = 
                                        		checkUnManagedFsCifsACLExistsInDB(_dbClient, cifsAcl.getNativeGuid());
                                        if(existingACL == null) {
                                        	newUnManagedCifsACLs.add(cifsAcl);
                                        } else {
                                        	newUnManagedCifsACLs.add(cifsAcl);
                                        	existingACL.setInactive(true);
                                        	oldUnManagedCifsACLs.add(existingACL);
                                        }
                                        
                                    }
                                    
                                    unManagedExportBatch.add(vnxufs);
                                    
                            	  }
                            }
                        
	                        if(noOfShares == 0  && !vnxufs.getHasExports()) {
	                            _logger.info("FileSystem "
	                                    + fsMountPath
	                                    + " does not have valid ViPR shares, hence this filesystem cannot be brought into ViPR DB");
	                            vnxufs.setHasShares(false);
	                            vnxufs.putFileSystemCharacterstics(UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_INGESTABLE.toString(), FALSE);
	                            vnxufs.putFileSystemCharacterstics(UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED.toString(), FALSE);
	                            vnxufs.setInactive(true);
	                            unManagedExportBatch.add(vnxufs);
	                        }
                        }
                        
                        if(unManagedExportBatch.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
                            //Add UnManagedFileSystem batch
                            // Update UnManagedFilesystem
                            _dbClient.persistObject(unManagedExportBatch);
                            unManagedExportBatch.clear();
                        }
                        
	                    if (newUnManagedCifsACLs.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
	                        //create new UnManagedCifsShareACL
	        				_logger.info("Saving Number of New UnManagedCifsShareACL(s) {}", newUnManagedCifsACLs.size());
	                        _dbClient.createObject(newUnManagedCifsACLs);
	                        newUnManagedCifsACLs.clear();
	        			}
	
	                    if (oldUnManagedCifsACLs.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
	                        //Update existing UnManagedCifsShareACL
	                        _logger.info("Saving Number of Old UnManagedCifsShareACL(s) {}", oldUnManagedCifsACLs.size());
	                        _dbClient.persistObject(oldUnManagedCifsACLs);
	                        oldUnManagedCifsACLs.clear();
	                    }
                    }
                }

            if (!unManagedExportBatch.isEmpty()) {
                // Update UnManagedFilesystem
                _dbClient.persistObject(unManagedExportBatch);
                unManagedExportBatch.clear();
            }
            
            if (!newUnManagedCifsACLs.isEmpty()) {
                //create new UnManagedCifsShareACL
				_logger.info("Saving Number of New UnManagedCifsShareACL(s) {}", newUnManagedCifsACLs.size());
                _dbClient.createObject(newUnManagedCifsACLs);
                newUnManagedCifsACLs.clear();
			}

            if (!oldUnManagedCifsACLs.isEmpty()) {
                //Update existing UnManagedCifsShareACL
                _logger.info("Saving Number of Old UnManagedCifsShareACL(s) {}", oldUnManagedCifsACLs.size());
                _dbClient.persistObject(oldUnManagedCifsACLs);
                oldUnManagedCifsACLs.clear();
            }
            
            // discovery succeeds
            storageSystem
			.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
					.toString());
            detailedStatusMessage = String.format("Discovery completed successfully for VNXFile shares: %s",
                    storageSystemId.toString());

        } catch (Exception ex) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            storageSystem
			.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
					.toString());
            detailedStatusMessage = String.format("Discovery failed for VNXFile cifs shares %s because %s",
                    storageSystemId.toString(), ex.getLocalizedMessage());
            _logger.error(detailedStatusMessage, ex);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
        
    }
    
   private List<UnManagedCifsShareACL>  applyCifsSecurityRules(UnManagedFileSystem vnxufs, String expPath,
		                        Map<String, String> fsExportInfo, StoragePort storagePort){
	   
	   List<UnManagedCifsShareACL> cifsACLs = new ArrayList<UnManagedCifsShareACL>();
	   
	   UnManagedCifsShareACL unManagedCifsShareACL = new UnManagedCifsShareACL();
	   String shareName = fsExportInfo.get(VNXFileConstants.SHARE_NAME);
       
       unManagedCifsShareACL.setShareName(shareName);
       
       //user
       unManagedCifsShareACL.setUser(FileControllerConstants.CIFS_SHARE_USER_EVERYONE);
       //permission
       unManagedCifsShareACL.setPermission(FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE);

       unManagedCifsShareACL.setId(URIUtil.createId(UnManagedCifsShareACL.class));

       //filesystem id
       unManagedCifsShareACL.setFileSystemId(vnxufs.getId());
  
       cifsACLs.add(unManagedCifsShareACL);
	   
	   return cifsACLs;
	   
   }
   private void discoverUnmanagedNewExports(AccessProfile profile) {
        
        // Get Storage System
        URI storageSystemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if(null == storageSystem){
            return;
        }

        String detailedStatusMessage = "Discovery of VNX Unmanaged Exports started";
        _logger.info(detailedStatusMessage);
        
		// Used to Save the rules to DB
		List<UnManagedFileExportRule> newUnManagedExportRules = new ArrayList<UnManagedFileExportRule>();
        List<UnManagedFileExportRule> oldUnManagedExportRules = new ArrayList<UnManagedFileExportRule>();


        try {
            
        	// Verification Utility
			UnManagedExportVerificationUtility validationUtility = new UnManagedExportVerificationUtility(
			        _dbClient);

            // Discover port groups (data mover ids) and group names (data mover names)
            Set <StorageHADomain>  activeDataMovers = discoverActiveDataMovers(storageSystem);

            // Reused from discoverAll
            // Discover ports (data mover interfaces) with the data movers in the active set.
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem, activeDataMovers);

            _logger.info("No of newly discovered port {}", ports.get(NEW).size());
            _logger.info("No of existing discovered port {}", ports.get(EXISTING).size());
            if(!ports.get(NEW).isEmpty()){
                _dbClient.createObject(ports.get(NEW));
            }
            
            List<StoragePort> allPortsList = ports.get(NEW);
            allPortsList.addAll(ports.get(EXISTING));

            Map<String, List<StoragePort>> allPorts = new ConcurrentHashMap<String, List<StoragePort>>();
            for (StoragePort sPort: allPortsList) {
                _logger.debug("DM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                List<StoragePort>  spList = allPorts.get(sPort.getStorageHADomain().toString());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);
                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }

            Map<String, List<StorageHADomain>> allVdms = discoverVdmPortGroups(storageSystem, activeDataMovers);
            if(!allVdms.get(NEW).isEmpty()){
                _dbClient.createObject(allVdms.get(NEW));
            }

            Set<StorageHADomain> allActiveVDMs = new HashSet();
            allActiveVDMs.addAll(allVdms.get(NEW));
            allActiveVDMs.addAll(allVdms.get(EXISTING));

            activeDataMovers.addAll(allVdms.get(NEW));
            activeDataMovers.addAll(allVdms.get(EXISTING));


            Map<String, List<StoragePort>> allVdmPorts =  discoverVdmPorts(storageSystem, allActiveVDMs);
            if(!allVdmPorts.get(NEW).isEmpty()){
                _dbClient.createObject(allVdmPorts.get(NEW));
            }

            List<StoragePort> allVDMPortsList = allVdmPorts.get(NEW);
            allVDMPortsList.addAll(allVdmPorts.get(EXISTING));

            for (StoragePort sPort: allVDMPortsList) {
                List<StoragePort>  spList = allPorts.get(sPort.getStorageHADomain().toString());
                _logger.debug("VDM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);
                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }
            
            List<UnManagedFileSystem> unManagedExportBatch = new ArrayList<>();
  
            for(StorageHADomain mover: activeDataMovers) {

                // Get storage port and name for the DM
                if(allPorts.get(mover.getId().toString()) == null || allPorts.get(mover.getId().toString()).isEmpty()) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.debug("No Ports found for {} {}", mover.getName(), mover.getAdapterName());
                    continue;
                }  else {
                    _logger.debug("Number of  Ports found for {} : {} ", mover.getName() +":" + mover.getAdapterName(),
                            allPorts.get(mover.getId().toString()).size());
                }
                Collections.shuffle(allPorts.get(mover.getId().toString()));
                StoragePort storagePort = allPorts.get(mover.getId().toString()).get(0);
                if (storagePort == null) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.debug("StoragePort is null");
                    continue;
                }
                //storagePort.setStorageHADomain(mover.getId());

                // Retrieve FS-mountpath map for the Data Mover.
                _logger.info("Retrieving FS-mountpath map for Data Mover {}.", 
                        mover.getAdapterName());
                VNXFileSshApi sshDmApi = new VNXFileSshApi();
                sshDmApi.setConnParams(storageSystem.getIpAddress(), storageSystem.getUsername(), 
                        storageSystem.getPassword());

                Map<String, String> fileSystemMountpathMap = sshDmApi.getFsMountpathMap(
                        mover.getAdapterName());

                Map <String, Map<String, String>> moverExportDetails =
                        sshDmApi.getNFSExportsForPath(mover.getAdapterName());

                Map<String, String> nameIdMap = getFsNameFsNativeIdMap(storageSystem);

                // Loop through the map and, if the file exists in DB, retrieve the 
                // export, process export, and associate export with the FS                    
                Set<String> fsNames = fileSystemMountpathMap.keySet();
                for (String fsName: fsNames) {
                    // Retrieve FS from DB.  If FS found, retrieve export and process
                    String fsMountPath = fileSystemMountpathMap.get(fsName);
                    
                    // Get FS ID for nativeGUID
                    //VNXFileSystem vnxFileSystems = discoverNamedFileSystem(storageSystem, fsName);
                    String fsId = nameIdMap.get(fsName);
                    _logger.debug("Resolved FileSystem name {} to native Id {}", fsName, fsId);
                    
                    UnManagedFileSystem vnxufs = null;
                    if (fsId != null){
                        String fsNativeGuid = NativeGUIDGenerator.
                                generateNativeGuidForPreExistingFileSystem(
                                        storageSystem.getSystemType(), storageSystem
                                        .getSerialNumber().toUpperCase(),
                                        fsId);

                        vnxufs = checkUnManagedFileSystemExistsInDB(fsNativeGuid);
                    }
      
                    if (vnxufs != null) {
                        // Get export info
                        int noOfExports = 0;
                        boolean inValidExports = false;
                        for(String expPath:moverExportDetails.keySet()){
                            if(!expPath.contains(fsMountPath)) {
                                //Ingore this path as it is not among the exports
                                continue;
                            } else {
                            	// We should process only FS and its sub-directory exports only.
                            	String subDir = expPath.substring(fsMountPath.length());
                            	if( !subDir.isEmpty() && !subDir.startsWith("/")){
                            		continue;
                            	}
                                _logger.info("Path : {} " , expPath);
                                noOfExports++;
                            }
                            
                            // Used as for rules validation
            				List<UnManagedFileExportRule> unManagedExportRules = new ArrayList<UnManagedFileExportRule>();
            				
                            Map<String, String> fsExportInfo = moverExportDetails.get(expPath);
                            if ((fsExportInfo != null) && (fsExportInfo.size() > 0)) {
                               // If multiple security flavors, do not add to ViPR DB
                               String securityFlavors = fsExportInfo.get(VNXFileConstants.SECURITY_TYPE);
                               if(securityFlavors == null || securityFlavors.length() == 0) {
                                   securityFlavors = "sys";
                               }
                               if (securityFlavors != null) {
                            	   
                            	   String fsMountPoint = storagePort.getPortNetworkId() + ":" + expPath;
                            	   
                            	   _logger.info("Associating FS export map for VNX UMFS {}", vnxufs.getLabel());
                            	   
                            	   associateExportWithFS(vnxufs, expPath, fsExportInfo, expPath,
                                           storagePort);
                            	   
                            	   _logger.debug("Export map for VNX UMFS {} = {}",  vnxufs.getLabel(), vnxufs.getFsUnManagedExportMap());
                            	   
                            	   List<UnManagedFileExportRule> exportRules = applyAllSecurityRules(vnxufs.getId(), expPath, fsMountPoint, securityFlavors,
                            			   																fsExportInfo);
                                    _logger.info("Number of export rules discovered for file system {} is {}", vnxufs.getId() + ":" + vnxufs.getLabel(), exportRules.size());

                                    for (UnManagedFileExportRule dbExportRule : exportRules) {
                                        _logger.info("Unmanaged File Export Rule : {}", dbExportRule);
                                        String fsExportRulenativeId = dbExportRule.getFsExportIndex();
                                        _logger.info("Native Id using to build Native Guid {}", fsExportRulenativeId);
                                        String fsUnManagedFileExportRuleNativeGuid = NativeGUIDGenerator
                                                .generateNativeGuidForPreExistingFileExportRule(
                                                        storageSystem, fsExportRulenativeId);
                                        _logger.info("Native GUID {}", fsUnManagedFileExportRuleNativeGuid);

                                        dbExportRule.setNativeGuid(fsUnManagedFileExportRuleNativeGuid);
                                        dbExportRule.setFileSystemId(vnxufs.getId());
                                        dbExportRule.setId(URIUtil.createId(UnManagedFileExportRule.class));
                                        // Build all export rules list.
                                        unManagedExportRules.add(dbExportRule);
                                    }

                                // Validate Rules Compatible with ViPR - Same rules should
                                    // apply as per API SVC Validations.
                                    if(!unManagedExportRules.isEmpty()) {
                                        boolean isAllRulesValid = validationUtility
                                                .validateUnManagedExportRules(unManagedExportRules, false);
                                        if (isAllRulesValid) {
                                            _logger.info("Validating rules success for export {}", expPath);
                                            for (UnManagedFileExportRule exportRule : unManagedExportRules) {
                                            	UnManagedFileExportRule existingRule = checkUnManagedFsExportRuleExistsInDB(_dbClient, exportRule.getNativeGuid());
                                                if( existingRule == null) {
                                                    newUnManagedExportRules.add(exportRule);
                                                } else {
                                                	// Remove the existing rule.
                                                	existingRule.setInactive(true);
                                                	_dbClient.persistObject(existingRule);
                                                	newUnManagedExportRules.add(exportRule);
                                                }
                                            }
                                            vnxufs.setHasExports(true);
                                            //Set the correct storage port
                                            if(null != storagePort){
                                                StringSet storagePorts = new StringSet();
                                                storagePorts.add(storagePort.getId().toString());
                                                vnxufs.getFileSystemInformation().put(
                                                        UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
                                            }
                                            _dbClient.persistObject(vnxufs);
                                            _logger.info("File System {} has Exports and their size is {}", vnxufs.getId(), newUnManagedExportRules.size());
                                        } else {
                                            _logger.warn("Validating rules failed for export {}. Ignoring to import these rules into ViPR DB", vnxufs);
                                            inValidExports = true;
                                        }
                                    } else {
                                        _logger.warn("Export discovery failed for  {}. Ignoring to import these rules into ViPR DB", vnxufs);
                                        inValidExports = true;
                                    }
                                    // Adding this additional logic to avoid OOM
                                    if (newUnManagedExportRules.size() == MAX_UMFS_RECORD_SIZE) {
                                        _logger.info("Saving Number of New UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
                                        _dbClient.createObject(newUnManagedExportRules);
                                        newUnManagedExportRules.clear();
                                    }

                                       // Adding this additional logic to avoid OOM
                                    if (oldUnManagedExportRules.size() == MAX_UMFS_RECORD_SIZE) {
                                         _logger.info("Saving Number of Existing UnManagedFileExportRule(s) {}", oldUnManagedExportRules.size());
                                         _dbClient.persistObject(oldUnManagedExportRules);
                                         oldUnManagedExportRules.clear();
                                    }
                                }
                            }
                        }
                        _logger.info("No of exports found for path {} = {} ", fsMountPath, noOfExports);

                        if(noOfExports == 0 || inValidExports) {
                            _logger.info("FileSystem " + fsMountPath + " does not have valid ViPR exports ");
                            vnxufs.setHasExports(false);
                        }
                        
                        unManagedExportBatch.add(vnxufs);
                        
                        if(unManagedExportBatch.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
                            //Add UnManagedFileSystem batch
                            // Update UnManagedFilesystem
                            _dbClient.persistObject(unManagedExportBatch);
                            unManagedExportBatch.clear();
                        }
                    }
                }
            }

            if (!unManagedExportBatch.isEmpty()) {
                // Update UnManagedFilesystem
                _dbClient.persistObject(unManagedExportBatch);
                unManagedExportBatch.clear();
            }
            
            if (!newUnManagedExportRules.isEmpty()) {
                //create new UnManagedExportFules
				_logger.info("Saving Number of New UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
                _dbClient.createObject(newUnManagedExportRules);
				newUnManagedExportRules.clear();
			}

            if (!oldUnManagedExportRules.isEmpty()) {
                //Update exisiting UnManagedExportFules
                _logger.info("Saving Number of Old UnManagedFileExportRule(s) {}", oldUnManagedExportRules.size());
                _dbClient.persistObject(oldUnManagedExportRules);
                oldUnManagedExportRules.clear();
            }

            
            // discovery succeeds
            storageSystem
			.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
					.toString());
            detailedStatusMessage = String.format("Discovery completed successfully for VNXFile export: %s",
                    storageSystemId.toString());

        } catch (Exception ex) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            storageSystem
			.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
					.toString());
            detailedStatusMessage = String.format("Discovery failed for VNXFile exports %s because %s",
                    storageSystemId.toString(), ex.getLocalizedMessage());
            _logger.error(detailedStatusMessage, ex);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
        
    }
   
	private List<UnManagedFileExportRule> applyAllSecurityRules(URI id, String exportPath, String mountPoint,
			String secFlavors, Map<String, String> fsExportInfo) {
		
		
		List<UnManagedFileExportRule> expRules = new ArrayList<UnManagedFileExportRule>();
		
		String[] secs = secFlavors.split(
		            VNXFileConstants.SECURITY_SEPARATORS);
			
		for (String sec : secs) {
			
			String anonUser = fsExportInfo.get(VNXFileConstants.ANON);
			StringSet readOnlyHosts = null;
			StringSet readWriteHosts = null;
			StringSet rootHosts = null;
			StringSet accessHosts = null;
			String hosts = null;
			
			hosts = fsExportInfo.get(VNXFileConstants.ACCESS); 
			if (hosts != null) {
	                accessHosts = new StringSet();
	                accessHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
	                        VNXFileConstants.HOST_SEPARATORS))));
	            }
			
            hosts = fsExportInfo.get(VNXFileConstants.RO);
            if (hosts != null) {
            	readOnlyHosts = new StringSet();
            	readOnlyHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS))));
            			
            }
            hosts = fsExportInfo.get(VNXFileConstants.RW);
            if (hosts != null) {
            	readWriteHosts = new StringSet();
            	readWriteHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS))));
            }
            
            hosts = fsExportInfo.get(VNXFileConstants.ROOT);
            if (hosts != null) {
            	rootHosts = new StringSet();
            	rootHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS))));
            }
            
            hosts = fsExportInfo.get(VNXFileConstants.ACCESS);
            if (hosts != null) {
                if(readWriteHosts == null)  {
                	readWriteHosts = new StringSet();
                }
                readWriteHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS))));
            }
            
            UnManagedFileExportRule unManagedfileExportRule = createUnManagedExportRule(id, exportPath,
            		mountPoint, sec, anonUser, accessHosts, readOnlyHosts, readWriteHosts, rootHosts);
            
            expRules.add(unManagedfileExportRule);
            
		} //end of for loop

		return expRules;
	}

    private Set<StorageHADomain>  discoverActiveDataMovers(StorageSystem storageSystem) {
        
        // Reused from discoverAll
        Set<StorageHADomain> activeDataMovers = new HashSet<StorageHADomain>();
        StringSet fileSharingProtocols = new StringSet();
        Map<String, List<StorageHADomain>> groups = discoverPortGroups(storageSystem, fileSharingProtocols);
        _logger.info("No of newly discovered groups {}", groups.get(NEW).size());
        _logger.info("No of existing discovered groups {}", groups.get(EXISTING).size());
        if(!groups.get(NEW).isEmpty()){
            _dbClient.createObject(groups.get(NEW));
        }

        // Keep a set of active data movers.  Data movers in 'standby' state are not added to the
        // database since they cannot be used in this state.
        List<StorageHADomain> newStorageDomains = groups.get(NEW);
        for (StorageHADomain mover : newStorageDomains) {
            activeDataMovers.add(mover);
        }
        List<StorageHADomain> existingStorageDomains = groups.get(EXISTING);
        for (StorageHADomain mover : existingStorageDomains) {
            activeDataMovers.add(mover);
        }
        
        for(StorageHADomain mover : activeDataMovers) {
            _logger.info("DataMover {} : {}", mover.getName(), mover.getAdapterName());
        }
        return activeDataMovers;
    }
    

    /**
     * Retrieve the FileSystem for the specified VNX File Storage Array
     *
     * @param system storage system information including credentials.
     * @return  list of Storage FileSystems
     * @throws VNXFileCollectionException
     */
    private List<VNXFileSystem> discoverAllFileSystems(StorageSystem system)
            throws VNXFileCollectionException, VNXException  {

        List<VNXFileSystem> fileSystems = new ArrayList<VNXFileSystem>();

        _logger.info("Start FileSystem discovery for storage system {}", system.getId());
        try {
            // Retrieve the list of FileSystem for the VNX File device.

            List<VNXFileSystem> vnxFileSystems = getAllFileSystem(system);
            _logger.info("Number filesytems found: {}", vnxFileSystems.size());
            if( vnxFileSystems != null) {
                // Create the list of FileSystem.
                for (VNXFileSystem vnxfs : vnxFileSystems) {

                    FileShare fs = null;

                    // Check if FileSystem was already discovered
                    URIQueryResultList results = new URIQueryResultList();
                    String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            system, vnxfs.getFsId()+"", NativeGUIDGenerator.FILESYSTEM);

                    if(checkStorageFileSystemExistsInDB(fsNativeGuid))
                        continue;

                    vnxfs.setFsNativeGuid(fsNativeGuid);
                    fileSystems.add(vnxfs);
                }
            }
            _logger.info("Number of FileSystem found {} and they are : ", fileSystems.size());

        } catch (IOException e) {
            _logger.error("I/O Exception: Discovery of FileSystem failed for storage system {} for {}",
                    system.getId(), e.getMessage());

            VNXFileCollectionException vnxe =
                    new VNXFileCollectionException("Storage FileSystem discovery error for storage system " + system.getId());
            vnxe.initCause(e);

            throw vnxe;
        }

        _logger.info("Storage FilesSystem discovery for storage system {} complete", system.getId());
        return fileSystems;
    }
        
    
    private VNXFileSystem discoverNamedFileSystem(StorageSystem system, String fsName) {
        
        List<VNXFileSystem> fileSystems = null;
        VNXFileSystem fileSystem = null;
        try {
            // Retrieve list of File Systems from the VNX file device.
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            reqAttributeMap.put(VNXFileConstants.FILESYSTEM_NAME, fsName);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(VNXFileConstants.VNX_FILE_SELECTED_FS));
            fileSystems = (List<VNXFileSystem>) _discExecutor.getKeyMap().get(VNXFileConstants.FILESYSTEMS);
            if ((fileSystems != null) && !(fileSystems.isEmpty())) {
                _logger.info("Number of file systems found: {}", fileSystems.size());
                fileSystem = fileSystems.get(0);
            } else {
                _logger.info("No File System was found on the VNX device");
            }
        } catch (BaseCollectionException ex) {
            _logger.error("VNX Exception: Discovery of File Systems failed for storage system {} due to {}",
                    system.getId(), ex.getMessage());
        }
        return fileSystem;
    }

    /**
     * If discovery fails, then mark the system as unreachable.  The
     * discovery framework will remove the storage system from the database.
     *
     * @param system  the system that failed discovery.
     */
    private void cleanupDiscovery(StorageSystem system) {
        try {
            system.setReachableStatus(false);
            _dbClient.persistObject(system);
        } catch (DatabaseException e) {
            _logger.error("discoverStorage failed.  Failed to update discovery status to ERROR.", e);
        }

    }

    public void setDiscExecutor( VNXFileDiscExecutor discExec ) {
        _discExecutor = discExec;
    }

    public VNXFileDiscExecutor getDiscExecutor() {
        return _discExecutor;
    }

    public void setDiscNamespaces(NamespaceList namespaces) {
        _discNamespaces = namespaces;
    }

    public NamespaceList getDiscNamespaces() {
        return _discNamespaces;
    }
    
    private List<VNXStoragePool> getStoragePools( final StorageSystem system)
            throws VNXException {

        List<VNXStoragePool> storagePools = null;
        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _logger.info("{}", _discExecutor);
            
            _discExecutor.setKeyMap(reqAttributeMap);
            _logger.info("{}",(Namespace) _discNamespaces.getNsList().get(
                    "vnxfileStoragePool"));
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileStoragePool"));
            storagePools = (ArrayList<VNXStoragePool>) _discExecutor
                    .getKeyMap().get(VNXFileConstants.STORAGEPOOLS);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get control station op failed", e);
        }
        return storagePools;
    }
    

    private VNXControlStation getControlStation( final StorageSystem system )
            throws VNXException {

        VNXControlStation station = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileControlStation"));
            
            station = (VNXControlStation) _discExecutor.getKeyMap().get(
                    VNXFileConstants.CONTROL_STATION_INFO);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get control station op failed", e);
        }

        return station;
    }
    
    private List<VNXDataMover> getPortGroups( final StorageSystem system )
            throws VNXException {

        List<VNXDataMover> dataMovers = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileStoragePortGroup"));
            dataMovers = (ArrayList<VNXDataMover>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.STORAGE_PORT_GROUPS);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get Port Groups op failed", e);
        }

        return dataMovers;
    }

    private boolean checkCifsEnabled( final StorageSystem system, VNXDataMover mover) throws VNXException {
        boolean cifsSupported = false;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);

            reqAttributeMap.put(VNXFileConstants.MOVER_ID, Integer.toString(mover.getId()));
            reqAttributeMap.put(VNXFileConstants.ISVDM, "false");

            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(VNXFileConstants.VNX_FILE_CIFS_CONFIG));

            cifsSupported = (Boolean)_discExecutor.getKeyMap().get(VNXFileConstants.CIFS_SUPPORTED);

        } catch (BaseCollectionException e) {
            throw new VNXException("check CIFS Enabled op failed", e);
        }

        return cifsSupported;
    }

    private  List<VNXCifsServer> getCifServers( final StorageSystem system, String moverId, String isVdm) throws VNXException {
        List<VNXCifsServer> cifsServers;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);

            reqAttributeMap.put(VNXFileConstants.MOVER_ID, moverId);
            reqAttributeMap.put(VNXFileConstants.ISVDM, isVdm);

            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(VNXFileConstants.VNX_FILE_CIFS_CONFIG));

            cifsServers = (List<VNXCifsServer>)_discExecutor.getKeyMap().get(VNXFileConstants.CIFS_SERVERS);

        } catch (BaseCollectionException e) {
            throw new VNXException("Get CifServers op failed", e);
        }

        return cifsServers;
    }

    private  List<VNXCifsServer> getCifServers( final StorageSystem system) throws VNXException {
        List<VNXCifsServer> cifsServers;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);

            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(VNXFileConstants.VNX_FILE_CIFS_CONFIG));

            cifsServers = (List<VNXCifsServer>)_discExecutor.getKeyMap().get(VNXFileConstants.CIFS_SERVERS);

        } catch (BaseCollectionException e) {
            throw new VNXException("Get CifServers op failed", e);
        }

        return cifsServers;
    }    

    private List<VNXDataMoverIntf> getPorts( final StorageSystem system )
            throws VNXException {

        List<VNXDataMoverIntf> dataMovers = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileStoragePort"));
            dataMovers = (ArrayList<VNXDataMoverIntf>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.STORAGE_PORTS);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get Port op failed", e);
        }

        return dataMovers;
    }

    private List<VNXVdm> getVdmPortGroups( final StorageSystem system )
            throws VNXException {

        List<VNXVdm> vdms = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileVdm"));
            vdms = (ArrayList<VNXVdm>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.VDM_INFO);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get Vdm Port Groups op failed", e);
        }

        return vdms;
    }


    private List<VNXDataMoverIntf> getVdmPorts( final StorageSystem system, final List<VNXVdm> vdms)
            throws VNXException {

        List<VNXDataMoverIntf> dataMoverInterfaces = null;
        List<VNXDataMoverIntf> vdmInterfaces = new ArrayList<VNXDataMoverIntf>();
        Map<String,VNXDataMoverIntf> dmIntMap = new HashMap();

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileStoragePort"));
            dataMoverInterfaces = (ArrayList<VNXDataMoverIntf>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.STORAGE_PORTS);
            //Make map
            for(VNXDataMoverIntf intf:dataMoverInterfaces){
               dmIntMap.put(intf.getName(), intf);
            }

            VNXFileSshApi sshDmApi = new VNXFileSshApi();
            sshDmApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());

            //collect VDM interfaces
            for(VNXVdm vdm:vdms){
                for(String vdmIF:vdm.getInterfaces()){
                    VNXDataMoverIntf vdmInterface = dmIntMap.get(vdmIF);
                    vdmInterfaces.add(vdmInterface);
                    _logger.info("Use this VDM interface {}", vdmIF);
                }
                //Collect NFS/CIFS interfaces from nas_server -info command. This will return
                //Interfaces assigned to VDM and not thru CIFS servers
                Map<String, String> vdmIntfs = sshDmApi.getVDMInterfaces(vdm.getVdmName());
                for(String vdmNFSIf:vdmIntfs.keySet()) {
                    VNXDataMoverIntf vdmInterface = dmIntMap.get(vdmNFSIf);
                    if(vdmInterface != null) {
                        _logger.info("Use this NFS VDM interface {} for {}", vdmInterface, vdmNFSIf);
                        vdmInterfaces.add(vdmInterface);
                        //Check if the interface is already on the VDM, if not, add it.
                        if(!vdm.getInterfaces().contains(vdmInterface.getName())) {
                            vdm.getInterfaces().add(vdmInterface.getName());
                        }
                    } else {
                        _logger.info("No interface found for {}", vdmNFSIf);
                    }
                }
            }

        } catch (BaseCollectionException e) {
            throw new VNXException("Get VDM Port op failed", e);
        }

        return vdmInterfaces;
    }

    private List<VNXFileSystem> getAllFileSystem( final StorageSystem system )
            throws VNXException {

        List<VNXFileSystem> fileSystems = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileSystem"));

            fileSystems = (ArrayList<VNXFileSystem>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.FILESYSTEMS);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get FileSystems op failed", e);
        }

        return fileSystems;
    }
    
    private void associateExportWithFS(UnManagedFileSystem vnxufs, 
            String exportPath, Map<String, String> fsExportInfo, String mountPath, StoragePort storagePort) {
        
        try {
            String security = fsExportInfo.get(VNXFileConstants.SECURITY_TYPE);
            if (security == null) {
                security = FileShareExport.SecurityTypes.sys.toString();
            } else {
            	String[] securityFlavorArr = security.split(
                        VNXFileConstants.SECURITY_SEPARATORS);
            	if(securityFlavorArr.length == 0) {
            		security = FileShareExport.SecurityTypes.sys.toString();
            	} else {
            		security = securityFlavorArr[0];
            	}
            }
            
            // Assign storage port to unmanaged FS
            vnxufs.getFileSystemInformation().remove(UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString());
            if (storagePort != null) {
                StringSet storagePorts = new StringSet();
                storagePorts.add(storagePort.getId().toString());
                vnxufs.getFileSystemInformation().put(
                        UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
            }
            
            // Get protocol, NFS by default
            String protocol = fsExportInfo.get(VNXFileConstants.PROTOCOL);
            if (protocol == null) {
                protocol = StorageProtocol.File.NFS.toString();
            }
            
            List<String> accessHosts = null;
            List<String> roHosts = null;
            List<String> rwHosts = null;
            List<String> rootHosts = null;
            
            // TODO all hosts
            String hosts = fsExportInfo.get(VNXFileConstants.ACCESS);
            if (hosts != null) {
                accessHosts = new ArrayList<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS)));
            }
            hosts = fsExportInfo.get(VNXFileConstants.RO);
            if (hosts != null) {
                roHosts = new ArrayList<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS)));;
            }
            hosts = fsExportInfo.get(VNXFileConstants.RW);
            if (hosts != null) {
                rwHosts = new ArrayList<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS)));;
            }
            hosts = fsExportInfo.get(VNXFileConstants.ACCESS);
            if (hosts != null) {
                if(rwHosts == null)  {
                    rwHosts = new ArrayList();
                }
                rwHosts.addAll(Arrays.asList(hosts.split(
                       VNXFileConstants.HOST_SEPARATORS)));
            }
            hosts = fsExportInfo.get(VNXFileConstants.ROOT);
            if (hosts != null) {
                rootHosts = new ArrayList<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS)));;
            }
            String anonUser = fsExportInfo.get(VNXFileConstants.ANON);
        
            // If both roHosts and rwHosts are null, accessHosts get "rw" 
            // permission.
            // If either roHosts or rwHosts is non-null, accessHosts get 
            // "ro" permission.
            if ((accessHosts != null) && (roHosts == null)) {
                // The non-null roHosts case is covered further below
                //Create a new unmanaged export
                UnManagedFSExport unManagedfileExport = createUnManagedExportWithAccessHosts(
                        accessHosts, rwHosts, exportPath, security, storagePort, anonUser, protocol);
                associateExportMapWithFS(vnxufs, unManagedfileExport);
                StringBuffer debugInfo = new StringBuffer();
                debugInfo.append("VNXFileExport: ");
                debugInfo.append(" Hosts : " + accessHosts.toString());
                debugInfo.append(" Mount point : " + exportPath);
                debugInfo.append(" Permission : " + unManagedfileExport.getPermissions());
                debugInfo.append(" Protocol : " + unManagedfileExport.getProtocol());
                debugInfo.append(" Security type : " + unManagedfileExport.getSecurityType());
                debugInfo.append(" Root user mapping : " + unManagedfileExport.getRootUserMapping());
                _logger.debug(debugInfo.toString());
            }
            
            if (roHosts != null) {
                UnManagedFSExport unManagedfileExport = createUnManagedExportWithRoHosts(
                        roHosts, accessHosts, exportPath, security, storagePort, anonUser, protocol);
                associateExportMapWithFS(vnxufs, unManagedfileExport);
                StringBuffer debugInfo = new StringBuffer();
                debugInfo.append("VNXFileExport: ");
                debugInfo.append(" Hosts : " + roHosts.toString());
                debugInfo.append(" Mount point : " + exportPath);
                debugInfo.append(" Permission : " + unManagedfileExport.getPermissions());
                debugInfo.append(" Protocol : " + unManagedfileExport.getProtocol());
                debugInfo.append(" Security type : " + unManagedfileExport.getSecurityType());
                debugInfo.append(" Root user mapping : " + unManagedfileExport.getRootUserMapping());
                _logger.debug(debugInfo.toString());
            }
            
            if (rwHosts != null) {
                UnManagedFSExport unManagedfileExport = createUnManagedExportWithRwHosts(
                        rwHosts, exportPath, security, storagePort, anonUser, protocol);
                associateExportMapWithFS(vnxufs, unManagedfileExport);
                StringBuffer debugInfo = new StringBuffer();
                debugInfo.append("VNXFileExport: ");
                debugInfo.append(" Hosts : " + rwHosts.toString());
                debugInfo.append(" Mount point : " + exportPath);
                debugInfo.append(" Permission : " + unManagedfileExport.getPermissions());
                debugInfo.append(" Protocol : " + unManagedfileExport.getProtocol());
                debugInfo.append(" Security type : " + unManagedfileExport.getSecurityType());
                debugInfo.append(" Root user mapping : " + unManagedfileExport.getRootUserMapping());
                _logger.debug(debugInfo.toString());
            }
            
            if (rootHosts != null) {
                UnManagedFSExport unManagedfileExport = createUnManagedExportWithRootHosts(
                        rootHosts, exportPath, security, storagePort, anonUser, protocol);
                // TODO Separate create map and associate
                associateExportMapWithFS(vnxufs, unManagedfileExport);
                StringBuffer debugInfo = new StringBuffer();
                debugInfo.append("VNXFileExport: ");
                debugInfo.append(" Hosts : " + rootHosts.toString());
                debugInfo.append(" Mount point : " + exportPath);
                debugInfo.append(" Permission : " + unManagedfileExport.getPermissions());
                debugInfo.append(" Protocol : " + unManagedfileExport.getProtocol());
                debugInfo.append(" Security type : " + unManagedfileExport.getSecurityType());
                debugInfo.append(" Root user mapping : " + unManagedfileExport.getRootUserMapping());
                _logger.info(debugInfo.toString());
            }
            
        } catch (Exception ex) {
            _logger.warn("VNX file export retrieve processor failed for path {}, cause {}", 
                    mountPath, ex);
        }
    }
    
    private String getMountPount(String shareName, StoragePort storagePort){

    	String mountPoint = null;
    	String portName = storagePort.getPortName();
    	if(storagePort.getPortNetworkId() != null){
    		portName = storagePort.getPortNetworkId();
    	}   
    	mountPoint = (portName != null)? "\\\\" + portName + "\\" + shareName : null;
    	return mountPoint;

    }
    private void associateCifsExportWithFS(UnManagedFileSystem vnxufs, 
            String exportPath, Map<String, String> fsExportInfo, StoragePort storagePort) {
        
        try {
            
            // Assign storage port to unmanaged FS
            if (storagePort != null) {
                StringSet storagePorts = new StringSet();
                storagePorts.add(storagePort.getId().toString());
                vnxufs.getFileSystemInformation().remove(UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString());
                vnxufs.getFileSystemInformation().put(
                        UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
            }
           
            String shareName = fsExportInfo.get(VNXFileConstants.SHARE_NAME);
            String mountPoint = getMountPount(shareName, storagePort);
            UnManagedSMBFileShare unManagedSMBFileShare = new UnManagedSMBFileShare();
            unManagedSMBFileShare.setName(shareName);
            unManagedSMBFileShare.setMountPoint(mountPoint);
            unManagedSMBFileShare.setPath(exportPath);
            //setting to default permission type for VNX
            unManagedSMBFileShare.setPermissionType(FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW);
            unManagedSMBFileShare.setDescription(fsExportInfo.get(VNXFileConstants.SHARE_COMMENT));
            int maxUsers = Integer.MAX_VALUE;
            if(Long.parseLong(fsExportInfo.get(VNXFileConstants.SHARE_MAXUSR)) < Integer.MAX_VALUE){
            	maxUsers = Integer.parseInt(fsExportInfo.get(VNXFileConstants.SHARE_MAXUSR));
            }
            unManagedSMBFileShare.setMaxUsers(maxUsers);
            unManagedSMBFileShare.setPortGroup(storagePort.getPortGroup());
            
            unManagedSMBFileShare.setPermission(ShareACL.SupportedPermissions.change.toString());
                         
            UnManagedSMBShareMap currUnManagedShareMap = vnxufs.getUnManagedSmbShareMap();
            if (currUnManagedShareMap == null) {
            	currUnManagedShareMap = new UnManagedSMBShareMap();
                vnxufs.setUnManagedSmbShareMap(currUnManagedShareMap);
            }
            
            if (currUnManagedShareMap.get(shareName) == null) {
            	currUnManagedShareMap.put(shareName, unManagedSMBFileShare);
                _logger.info("associateCifsExportWithFS - no SMBs already exists for share {}",
                		shareName);
            } else {
            	// Remove the existing and add the new share
            	currUnManagedShareMap.remove(shareName);
            	currUnManagedShareMap.put(shareName, unManagedSMBFileShare);
                _logger.warn("associateSMBShareMapWithFS - Identical export already exists for mount path {} Overwrite",
                		shareName);
            }              
            
        } catch (Exception ex) {
            _logger.warn("VNX file share retrieve processor failed for path {}, cause {}", 
            		exportPath, ex);
        }
    }
    
    private UnManagedFSExport createUnManagedExportWithAccessHosts(List<String> accessHosts, 
            List<String> rwHosts, String mountPath, String security, StoragePort storagePort, 
            String anonUser, String protocol) {
        UnManagedFSExport unManagedfileExport = new UnManagedFSExport();
        setupUnManagedFSExportProperties(unManagedfileExport, mountPath, 
                security, storagePort, anonUser, protocol);
        unManagedfileExport.setClients(accessHosts);
        if (rwHosts == null) {
            unManagedfileExport.setPermissions(VNXFileConstants.RW);
        } else {
            unManagedfileExport.setPermissions(VNXFileConstants.RO);
        }
        return unManagedfileExport;
    }
    
    private UnManagedFSExport createUnManagedExportWithRoHosts(List<String> roHosts,
            List<String> accessHosts, String mountPath, String security, 
            StoragePort storagePort, String anonUser, String protocol) {
        UnManagedFSExport unManagedfileExport = new UnManagedFSExport();
        setupUnManagedFSExportProperties(unManagedfileExport, mountPath, 
                security, storagePort, anonUser, protocol);
        List<String> readOnlyHosts = roHosts;
        if (accessHosts != null) {
            for (String accHost: accessHosts) {
                if (!(readOnlyHosts.contains(accHost))) {
                    readOnlyHosts.add(accHost);
                }
            }
        }
        unManagedfileExport.setClients(readOnlyHosts);
        unManagedfileExport.setPermissions(VNXFileConstants.RO);
        return unManagedfileExport;
    }
    
    private UnManagedFSExport createUnManagedExportWithRwHosts(
            List<String> rwHosts, String mountPath, String security, 
            StoragePort storagePort, String anonUser, String protocol) {
        UnManagedFSExport unManagedfileExport = new UnManagedFSExport();
        setupUnManagedFSExportProperties(unManagedfileExport, mountPath, 
                security, storagePort, anonUser, protocol);
        unManagedfileExport.setClients(rwHosts);
        unManagedfileExport.setPermissions(VNXFileConstants.RW);
        return unManagedfileExport;
    }
    
    private UnManagedFSExport createUnManagedExportWithRootHosts(
            List<String> rootHosts, String mountPath, String security, 
            StoragePort storagePort, String anonUser, String protocol) {
        UnManagedFSExport unManagedfileExport = new UnManagedFSExport();
        setupUnManagedFSExportProperties(unManagedfileExport, mountPath, 
                security, storagePort, anonUser, protocol);
        unManagedfileExport.setClients(rootHosts);
        unManagedfileExport.setPermissions(VNXFileConstants.ROOT);
        return unManagedfileExport;
    }
    
    private UnManagedFileExportRule createUnManagedExportRule(URI id, String exportPath, String mountPoint,
    		String securityFlavor, String anonUser, StringSet accessHosts,
    		StringSet roHosts, StringSet rwHosts, StringSet rootHosts) {
    	
    	UnManagedFileExportRule umfsExpRule = new UnManagedFileExportRule();
    	// Don't create the ID here ...
    	umfsExpRule.setFileSystemId(id);
    	umfsExpRule.setAnon(anonUser);
    	umfsExpRule.setExportPath(exportPath);
    	umfsExpRule.setMountPoint(mountPoint);
    	umfsExpRule.setSecFlavor(securityFlavor);
    	
    	if (anonUser != null) {
            if (anonUser.equalsIgnoreCase(VNXFileConstants.ROOT_ANON_USER)) {
            	umfsExpRule.setAnon(VNXFileConstants.ROOT);
            } else {
            	umfsExpRule.setAnon(anonUser);
            }
        } else {
        	umfsExpRule.setAnon(VNXFileConstants.NOBODY);
        }
    	
    	if(accessHosts != null && roHosts == null) {
    		if (rwHosts == null) {
    			umfsExpRule.setReadWriteHosts(accessHosts);
            } else {
            	umfsExpRule.setReadOnlyHosts(accessHosts);
            }
    		
    	}
    	if(roHosts != null) {
	    	StringSet readOnlyHosts = roHosts;
	    	
	    	 if (accessHosts != null) {
	             for (String accHost: accessHosts) {
	                 if (!(readOnlyHosts.contains(accHost))) {
	                     readOnlyHosts.add(accHost);
	                 }
	             }
	         }
	    	 umfsExpRule.setReadOnlyHosts(readOnlyHosts);
    	}
    	
    	if(rwHosts != null) {
    		umfsExpRule.setReadWriteHosts(rwHosts);
    	}
    	
    	if(rootHosts != null) {
    		umfsExpRule.setRootHosts(rootHosts);
    	}
    	
        return umfsExpRule;
    }
    
    private void setupUnManagedFSExportProperties(
            UnManagedFSExport unManagedfileExport, String mountPath, 
            String security, StoragePort storagePort, String anonUser,
            String protocol) {
        unManagedfileExport.setMountPoint(storagePort.getPortNetworkId() + ":" + mountPath);
        unManagedfileExport.setPath(mountPath);
        unManagedfileExport.setMountPath(mountPath);
        unManagedfileExport.setProtocol(protocol);
        unManagedfileExport.setSecurityType(security);
        unManagedfileExport.setStoragePortName(storagePort.getPortName());
        unManagedfileExport.setStoragePort(storagePort.getId().toString());
        if (anonUser != null) {
            if (anonUser.equalsIgnoreCase(VNXFileConstants.ROOT_ANON_USER)) {
                unManagedfileExport.setRootUserMapping(VNXFileConstants.ROOT);
            } else {
                unManagedfileExport.setRootUserMapping(anonUser);
            }
        } else {
            unManagedfileExport.setRootUserMapping(VNXFileConstants.NOBODY);
        }
        _logger.debug("setupUnManagedFSExportProperties ExportKey : {} ", unManagedfileExport.getFileExportKey());
        _logger.debug("setupUnManagedFSExportProperties Path : {} ", unManagedfileExport.getPath());
        _logger.debug("setupUnManagedFSExportProperties Mount Path :{} ", unManagedfileExport.getMountPath());
        _logger.debug("setupUnManagedFSExportProperties Mount Point :{} ", unManagedfileExport.getMountPoint());
    }
    
    private void associateExportMapWithFS(UnManagedFileSystem vnxufs, 
            UnManagedFSExport unManagedfileExport) {
        // TODO: create - separate
        UnManagedFSExportMap currUnManagedExportMap = vnxufs.getFsUnManagedExportMap();
        if (currUnManagedExportMap == null) {
            currUnManagedExportMap = new UnManagedFSExportMap();
            vnxufs.setFsUnManagedExportMap(currUnManagedExportMap);
        }
        String exportKey = unManagedfileExport.getFileExportKey();
        if (currUnManagedExportMap.get(exportKey) == null) {
            currUnManagedExportMap.put(exportKey, unManagedfileExport);
            _logger.debug("associateExportMapWithFS {} no export already exists for mount path {}",
                    exportKey, unManagedfileExport.getMountPath());
        } else {
            currUnManagedExportMap.put(exportKey, unManagedfileExport);
            _logger.warn("associateExportMapWithFS {} Identical export already exists for mount path {} Overwrite",
                    exportKey, unManagedfileExport.getMountPath());
        }   
        
    }

    /**
     * create StorageFileSystem Info Object
     *
     * @param unManagedFileSystem
     * @param unManagedFileSystemNativeGuid
     * @param system
     * @param pool
     * @param storagePort
     * @param fileSystem
     * @return UnManagedFileSystem
     * @throws IOException
     * @throws VNXFileCollectionException
     */
    private UnManagedFileSystem createUnManagedFileSystem(
            UnManagedFileSystem unManagedFileSystem,
            String unManagedFileSystemNativeGuid, StorageSystem system, StoragePool pool,
            StoragePort storagePort, VNXFileSystem fileSystem) throws IOException, VNXFileCollectionException {
        if (null == unManagedFileSystem) {
            unManagedFileSystem = new UnManagedFileSystem();
            unManagedFileSystem.setId(URIUtil
                    .createId(UnManagedFileSystem.class));
            unManagedFileSystem.setNativeGuid(unManagedFileSystemNativeGuid);
            unManagedFileSystem.setStorageSystemUri(system.getId());
            unManagedFileSystem.setStoragePoolUri(pool.getId());
        }

        
        
        Map<String, StringSet> unManagedFileSystemInformation = new HashMap<String, StringSet>();
        StringMap unManagedFileSystemCharacteristics = new StringMap();

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_SNAP_SHOT.toString(),
                FALSE);

        if(fileSystem.getType().equals(UnManagedDiscoveredObject.SupportedProvisioningType.THICK.name())) {
        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                        .toString(), FALSE);
        }
        else
        {
            unManagedFileSystemCharacteristics.put(
                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                            .toString(), TRUE);
        }
        
        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                        .toString(), TRUE);

        if (null != system) {
            StringSet systemTypes = new StringSet();
            systemTypes.add(system.getSystemType());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.SYSTEM_TYPE.toString(),
                    systemTypes);
        }

        if (null != pool) {
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_POOL.toString(),
                    pools);
            // We should check matched vpool based on storagepool of type for given fs. 
            // In vipr, storagepool of thin is taken as THICK
            StringSet matchedVPools =  DiscoveryUtils.getMatchedVirtualPoolsForPool(_dbClient, pool.getId());
            if (unManagedFileSystemInformation.containsKey(UnManagedFileSystem.SupportedFileSystemInformation.
                    SUPPORTED_VPOOL_LIST.toString())) {
                
                if (null != matchedVPools && matchedVPools.isEmpty()) {
                    // replace with empty string set doesn't work, hence added explicit code to remove all
                    unManagedFileSystemInformation.get(
                             SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).clear();
                } else {
                    // replace with new StringSet
                    unManagedFileSystemInformation.get(
                         SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).replace(matchedVPools);
                 _logger.info("Replaced Pools :"+Joiner.on("\t").join( unManagedFileSystemInformation.get(
                         SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString())));
                }
            } else {
                unManagedFileSystemInformation
                .put(UnManagedFileSystem.SupportedFileSystemInformation.SUPPORTED_VPOOL_LIST
                        .toString(), matchedVPools);
            }
           
        }

        if(null != storagePort){
            StringSet storagePorts = new StringSet();
            storagePorts.add(storagePort.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
        }

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_INGESTABLE
                        .toString(), TRUE);
        //Set attributes of FileSystem
        StringSet fsPath = new StringSet();
        fsPath.add("/"+fileSystem.getFsName());

        StringSet fsMountPath = new StringSet();
        fsMountPath.add("/"+fileSystem.getFsName());

        StringSet fsName = new StringSet();
        fsName.add(fileSystem.getFsName());

        StringSet fsId = new StringSet();
        fsId.add(fileSystem.getFsId()+"");
        
        unManagedFileSystem.setLabel(fileSystem.getFsName());

        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.NAME.toString(), fsName);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.NATIVE_ID.toString(), fsId);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.DEVICE_LABEL.toString(), fsName);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PATH.toString(), fsPath);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.MOUNT_PATH.toString(), fsMountPath);


        StringSet allocatedCapacity = new StringSet();
        String usedCapacity = "0";
        if(fileSystem.getUsedCapacity() != null) {
            usedCapacity = fileSystem.getUsedCapacity();
        }
        allocatedCapacity.add(usedCapacity);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.ALLOCATED_CAPACITY
                        .toString(), allocatedCapacity);

        StringSet provisionedCapacity = new StringSet();
        String capacity = "0";
        if(fileSystem.getTotalCapacity() != null) {
            capacity = fileSystem.getTotalCapacity();
        }
        provisionedCapacity.add(capacity);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PROVISIONED_CAPACITY
                        .toString(), provisionedCapacity);

        // Add fileSystemInformation and Characteristics.
        unManagedFileSystem
                .addFileSystemInformation(unManagedFileSystemInformation);
        unManagedFileSystem
                .setFileSystemCharacterstics(unManagedFileSystemCharacteristics);



        return unManagedFileSystem;
    }

    private Map<String, Object> getRequestParamsMap(final StorageSystem system) {
        
        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();
        reqAttributeMap.put(VNXFileConstants.DEVICETYPE, system.getSystemType());
        reqAttributeMap.put(VNXFileConstants.DBCLIENT, _dbClient);
        reqAttributeMap.put(VNXFileConstants.USERNAME, system.getUsername());
        reqAttributeMap.put(VNXFileConstants.USER_PASS_WORD, system.getPassword());
        reqAttributeMap.put(VNXFileConstants.PORTNUMBER, system.getPortNumber());

        AccessProfile profile = new AccessProfile();
        profile.setIpAddress(system.getIpAddress());

        reqAttributeMap.put(VNXFileConstants.URI, getServerUri(profile));
        reqAttributeMap.put(VNXFileConstants.AUTHURI, getLoginUri(profile));
        return reqAttributeMap;
    }


    /**
     * check Storage fileSystem exists in DB
     *
     * @param nativeGuid
     * @return
     * @throws java.io.IOException
     */
    protected boolean checkStorageFileSystemExistsInDB(String nativeGuid)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemNativeGUIdConstraint(nativeGuid), result);
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
        	URI fileSystemtURI = iter.next();
        	FileShare fileShare = _dbClient.queryObject(FileShare.class, fileSystemtURI);
        	if(fileShare!=null && !fileShare.getInactive()){
        		return true;
        	}
        }
        return false;
    }

    /**
     * check Pre Existing Storage filesystem exists in DB
     *
     * @param nativeGuid
     * @return unManageFileSystem
     * @throws IOException
     */
    protected UnManagedFileSystem checkUnManagedFileSystemExistsInDB(
            String nativeGuid) throws IOException {
        UnManagedFileSystem filesystemInfo = null;
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemInfoNativeGUIdConstraint(nativeGuid), result);
        List<URI> filesystemUris = new ArrayList<URI>();
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI unFileSystemtURI = iter.next();
            filesystemUris.add(unFileSystemtURI);
        }
        for (URI fileSystemURI : filesystemUris){
       	 filesystemInfo = _dbClient.queryObject(UnManagedFileSystem.class,
       			 fileSystemURI);
       	 if(filesystemInfo!=null && !filesystemInfo.getInactive()){
       		return filesystemInfo;
       	 }
       }
          
       return null;

    }
    

    /*
     * get Storage Pool
     * @return
     */
    private StoragePool getStoragePool(StorageSystem storageSystem, String poolId) throws IOException {
        StoragePool storagePool = null;
        // Check if storage pool was already discovered
        URIQueryResultList results = new URIQueryResultList();
        String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                storageSystem, poolId, NativeGUIDGenerator.POOL);
        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePoolByNativeGuidConstraint(poolNativeGuid),
                results);
        Iterator<URI> storagePoolIter = results.iterator();
        while (results.iterator().hasNext()) {
            StoragePool tmpPool = _dbClient.queryObject(StoragePool.class, results.iterator().next());
            if (tmpPool!=null && !tmpPool.getInactive()
            		&& tmpPool.getStorageDevice().equals(storageSystem.getId())) {
                storagePool = tmpPool;
                _logger.debug("Found StoragePool {} at {}", storagePool.getPoolName(), poolNativeGuid);
                break;
            }
        }

        return storagePool;
    }

    private StoragePort getStoragePortPool(StorageSystem storageSystem)
            throws IOException {
        StoragePort storagePort = null;
        // Check if storage port was already discovered
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystem.getId()),
                storagePortURIs);
        Iterator<URI> storagePortIter = storagePortURIs.iterator();
        while(storagePortIter.hasNext()){
            URI storagePortURI = storagePortIter.next();
            storagePort = _dbClient.queryObject(StoragePort.class,
                    storagePortURI);
            if(storagePort!=null && !storagePort.getInactive()){
            	_logger.debug("found a port for storage system  {} {}",
                        storageSystem.getSerialNumber(), storagePort);
            	return storagePort;
            }
        }
        return null;
    }

    private Map<String, StorageHADomain> getAllDataMovers(StorageSystem storageSystem)
            throws IOException {

        Map <String, StorageHADomain> allDataMovers = new ConcurrentHashMap<>();

        List<URI> storageAdapterURIs = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceStorageHADomainConstraint(storageSystem
                                .getId()));
        List<StorageHADomain> dataMovers = _dbClient.queryObject(
                StorageHADomain.class, storageAdapterURIs);

        for(StorageHADomain dm:dataMovers){
            if(!dm.getInactive() && !dm.getVirtual()) {
                _logger.info("found a Physical StorageHADomain for storage system  {} {}",
                        storageSystem.getSerialNumber(), dm.getAdapterName());
                allDataMovers.put(dm.getAdapterName(), dm);
            }
        }
        return allDataMovers;
    }

    private Map<String, StorageHADomain>  getAllVDMs(StorageSystem storageSystem)
            throws IOException {

        Map <String, StorageHADomain> allVDMs = new ConcurrentHashMap<>();

        List<URI> storageAdapterURIs = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceStorageHADomainConstraint(storageSystem
                                .getId()));
        List<StorageHADomain> dataMovers = _dbClient.queryObject(
                StorageHADomain.class, storageAdapterURIs);

        for(StorageHADomain dm:dataMovers){
            if(!dm.getInactive() && dm.getVirtual()) {
                _logger.info("found a Virtual StorageHADomain for storage system  {} {}",
                        storageSystem.getSerialNumber(), dm.getAdapterName());
                allVDMs.put(dm.getAdapterName(), dm);
            }
        }
        return allVDMs;
    }


    private  HashMap<String, List<StoragePort>> getAllStoragePort(StorageSystem storageSystem)
            throws IOException {

        HashMap<String, List<StoragePort>> ports = new HashMap<>();

        ArrayList<StoragePort> allVirtualStoragePorts = new ArrayList<>();
        ArrayList<StoragePort> allPhysicalStoragePorts = new ArrayList<>();

        List<URI> storagePortURIs = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceStoragePortConstraint(storageSystem
                                .getId()));

        List<StoragePort> storagePorts = _dbClient.queryObject(
                StoragePort.class, storagePortURIs);

        for(StoragePort sp:storagePorts){
            URI moverOrVdmURI = sp.getStorageHADomain();
            if(!sp.getInactive() && moverOrVdmURI != null) {
                StorageHADomain moverOrVdm= _dbClient.queryObject(StorageHADomain.class,
                        moverOrVdmURI);
                if(moverOrVdm != null) {
                    if (moverOrVdm.getVirtual()) {
                        allVirtualStoragePorts.add(sp);
                    } else {
                        allPhysicalStoragePorts.add(sp);
                    }
                }
            }
        }
        //return ports;
        ports.put(VIRTUAL, allVirtualStoragePorts);
        ports.put(PHYSICAL, allPhysicalStoragePorts);
        return ports;

    }

    private List<StoragePort> getAllPhysicalStoragePort(StorageSystem storageSystem)
            throws IOException {
        return getAllStoragePort(storageSystem, false);
    }

    private List<StoragePort> getAllVirtualStoragePort(StorageSystem storageSystem)
            throws IOException {
        return getAllStoragePort(storageSystem, true);
    }

    private  List<StoragePort> getAllStoragePort(StorageSystem storageSystem, Boolean isVirtual)
            throws IOException {

        ArrayList<StoragePort> allStoragePorts = new ArrayList<>();

        List<URI> storagePortURIs = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceStoragePortConstraint(storageSystem
                                .getId()));

        List<StoragePort> storagePorts = _dbClient.queryObject(
                StoragePort.class, storagePortURIs);

        for(StoragePort sp:storagePorts){
            URI moverOrVdmURI = sp.getStorageHADomain();
            if(!sp.getInactive() && moverOrVdmURI != null) {
                StorageHADomain moverOrVdm= _dbClient.queryObject(StorageHADomain.class,
                        moverOrVdmURI);
                if(moverOrVdm != null) {
                    if (moverOrVdm.getVirtual() == isVirtual) {
                        allStoragePorts.add(sp);
                    }
                }
            }
        }
        //return ports;
        return allStoragePorts;
    }

    private Map<String, String> getFsNameFsNativeIdMap(StorageSystem storageSystem){
        HashMap<String, String> nameNativeIdMap = new HashMap<>();

        List<URI> umFsURIs = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceUnManagedFileSystemConstraint(storageSystem
                                .getId()));

        List<UnManagedFileSystem> umFSs = _dbClient.queryObject(
                UnManagedFileSystem.class, umFsURIs);

        for(UnManagedFileSystem umFS:umFSs){
            String fsName = extractValueFromStringSet(
                    UnManagedFileSystem.SupportedFileSystemInformation.NAME.toString(),
                    umFS.getFileSystemInformation());

            String fsNativeId = extractValueFromStringSet(
                    UnManagedFileSystem.SupportedFileSystemInformation.NATIVE_ID.toString(),
                    umFS.getFileSystemInformation());

            nameNativeIdMap.put(fsName, fsNativeId);
            _logger.debug("getFsNameFsNativeIdMap {} : {}", fsName, fsNativeId);
        }

        return nameNativeIdMap;
    }


    public static String extractValueFromStringSet(String key, StringSetMap volumeInformation) {
        try {
            StringSet availableValueSet = volumeInformation.get(key);
            if (null != availableValueSet) {
                for (String value : availableValueSet) {
                    return value;
                }
            }
        } catch (Exception e) {}
        return null;
    }

}

