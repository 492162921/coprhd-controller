/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getStorageDeviceRemoteGroupsConstraint;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToNativeID;
import static com.google.common.base.Predicates.and;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.providerfinders.FindProviderFactory;
import com.emc.storageos.volumecontroller.impl.providerfinders.FindProviderStrategy;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.CollectorFactory;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.CollectorStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.exceptions.RemoteGroupAssociationNotFoundException;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;

/**
 * Created by bibbyi1 on 4/15/2015.
 */
public class SRDFUtils implements SmisConstants {
    private static final Logger log = LoggerFactory.getLogger(SRDFUtils.class);

    private DbClient dbClient;
    private CIMObjectPathFactory cimPath;
    private SmisCommandHelper helper;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCimObjectPathFactory(CIMObjectPathFactory cimPath) {
        this.cimPath = cimPath;
    }

    public void setHelper(SmisCommandHelper helper) {
        this.helper = helper;
    }

    public CIMInstance getInstance(final CIMObjectPath path, final StorageSystem sourceSystem) {
        try {
            return helper.checkExists(sourceSystem, path, false, false);
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Given a collection of StorageSynchronization instances, filter out the
     * instances with any state considered "broken" leaving only the active ones.
     *
     * This method is useful in determining if any synchronizations as part of a
     * GroupSynchronized require pausing, as checking the state of a GroupSynchronized
     * instance directly may report an unhelpful state of "MIXED".
     *
     * @param synchronizations  Collection of StorageSynchronized instances to filter
     * @param provider          Represents a storage system with references to the StorageSynchronized instances.
     * @return                  A collection of zero, one or more active StorageSynchronized paths.
     */
    public Collection<CIMObjectPath> filterActiveLinks(Collection<CIMObjectPath> synchronizations,
                                                       StorageSystem provider) {
        return filter(synchronizations, activeLinkPredicate(provider));
    }

    /**
     * Given a collection of StorageSynchronization instances, filter out the instances
     * that are considered "active", leaving only the broken ones.
     *
     * @param synchronizations  Collection of StorageSynchronized instances to filter
     * @param provider          Represents a storage system with references to the StorageSynchronized instances.
     * @return                  A collection of zero, one or more broken StorageSynchronized paths.
     */
    public Collection<CIMObjectPath> filterBrokenLinks(Collection<CIMObjectPath> synchronizations,
                                                       StorageSystem provider) {
        return filter(synchronizations, not(activeLinkPredicate(provider)));
    }

    public boolean isBroken(final CIMInstance syncInstance) {
        if (null == syncInstance) return false;
        String copyState = syncInstance.getPropertyValue(CP_COPY_STATE).toString();
        // Solutions Enabler may report a Split status as Failed Over, for legacy reasons.
        if (String.valueOf(BROKEN).equalsIgnoreCase(copyState)
                || String.valueOf(FRACTURED).equalsIgnoreCase(copyState)
                || String.valueOf(SPLIT).equalsIgnoreCase(copyState)
                || String.valueOf(SUSPENDED).equalsIgnoreCase(copyState)
                || String.valueOf(FAILED_OVER).equalsIgnoreCase(copyState)) {
            return true;
        }
        return false;
    }

    public CIMObjectPath getGroupSynchronized(final Volume targetVolume,
                                                 final StorageSystem sourceSystem) {
        RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                targetVolume.getSrdfGroup());
        CIMObjectPath sourceGroupPath = null;
        CIMObjectPath targetGroupPath = null;
        if (!NullColumnValueGetter.isNotNullValue(group.getSourceReplicationGroupName()) ||
                !NullColumnValueGetter.isNotNullValue(group.getTargetReplicationGroupName())) {
            return null;
        } else {
            sourceGroupPath = cimPath.getReplicationGroupObjectPath(sourceSystem,
                    group.getSourceReplicationGroupName());
            targetGroupPath = cimPath.getReplicationGroupObjectPath(sourceSystem,
                    group.getTargetReplicationGroupName());
        }
        return cimPath.getGroupSynchronized(sourceGroupPath, targetGroupPath);
    }

    public CIMObjectPath getStorageSynchronizedObject(final StorageSystem sourceSystem, final Volume source,
            final Volume target, final StorageSystem activeProviderSystem) {
        CloseableIterator<CIMObjectPath> iterator = null;
        try {
            // If the Source Provider is down, make use of target provider to
            // find the Sync Paths. 
            // null check makes the caller not to check liveness for multiple volumes in loop.
            boolean isSourceActiveNow = (null == activeProviderSystem || URIUtil.equals(activeProviderSystem.getId(), sourceSystem.getId()));
            String nativeIdToUse = (isSourceActiveNow) ? source.getNativeId() : target.getNativeId();
            // Use the activeSystem always.
            StorageSystem systemToUse = (isSourceActiveNow) ? sourceSystem : activeProviderSystem;
            if (null != activeProviderSystem) {
                log.info("sourceSystem, activeProviderSystem: {} {}", sourceSystem.getNativeGuid(), activeProviderSystem.getNativeGuid());
            }

            CIMObjectPath volumePath = cimPath.getVolumePath(systemToUse, nativeIdToUse);
            log.info("Volume Path {}", volumePath.toString());
            if (volumePath == null) {
                throw new IllegalStateException("Volume not found : " + source.getNativeId());
            }
            iterator = helper.getReference(systemToUse, volumePath, SE_STORAGE_SYNCHRONIZED_SV_SV, null);
            while (iterator.hasNext()) {
                CIMObjectPath reference = iterator.next();
                if (reference.toString().contains(nativeIdToUse)) {
                    log.info("Storage Synchronized  reference {}", reference.toString());
                    return reference;
                }
            }
        } catch (Exception e) {
            log.error("Failed to acquire synchronization instance", e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
        return null;
    }

    public Collection<CIMObjectPath> getStorageSynchronizationsInRemoteGroup(StorageSystem provider, Volume targetVolume) {
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, targetVolume.getStorageController());
        CIMObjectPath objectPath = cimPath.getBlockObjectPath(targetSystem, targetVolume);

        CIMObjectPath remoteGroupPath = getRemoteGroupPath(provider, objectPath);
        List<CIMObjectPath> volumePathsInRemoteGroup = getVolumePathsInRemoteGroup(provider, remoteGroupPath);

        List<CIMObjectPath> result = new ArrayList<>();
        for (CIMObjectPath volumePath : volumePathsInRemoteGroup) {
            CIMObjectPath storageSync = getStorageSynchronizationFromVolume(provider, volumePath);
            result.add(storageSync);
        }

        return result;
    }

    public Collection<CIMObjectPath> getSynchronizations(StorageSystem activeProviderSystem, Volume sourceVolume,
            Volume targetVolume) throws WBEMException {
        return getSynchronizations(activeProviderSystem, sourceVolume, targetVolume, true);
    }

    public Collection<CIMObjectPath> getSynchronizations(StorageSystem activeProviderSystem, Volume sourceVolume,
            Volume targetVolume, boolean includeGroup) throws WBEMException {
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, targetVolume.getStorageController());

        List<CIMObjectPath> result = new ArrayList<>();
        if (sourceVolume.hasConsistencyGroup() && includeGroup) {
            result.addAll(getConsistencyGroupSyncPairs(sourceSystem, sourceVolume, targetSystem, targetVolume,
                    activeProviderSystem));
        } else {
            CIMObjectPath objectPath = getStorageSynchronizedObject(sourceSystem, sourceVolume, targetVolume,
                    activeProviderSystem);
            if (objectPath != null) {
                result.add(objectPath);
            }
        }
        return result;
    }

    /**
     * Gets associated ViPR volumes based on its SRDF configuration.
     * Async/Sync with CG -> All volumes in CG
     * Async without CG   -> All volumes in RDF group
     * Sync  without CG   -> Single volume
     *
     * @param system The provider system to collect synchronization instances from.
     * @param target The subject of the association query.
     * @return A list of Volumes
     */
    public List<Volume> getAssociatedVolumes(StorageSystem system, Volume target) {
        CollectorFactory collectorFactory = new CollectorFactory(dbClient, this);
        CollectorStrategy collector = collectorFactory.getCollector(target, false);

        Collection<CIMObjectPath> syncPaths = collector.collect(system, target);
        Collection<SynchronizedVolumePair> volumePairs = transform(syncPaths, toSynchronizedVolumePairFn());

        Set<URI> volumeURIs = new HashSet<>();
        URIQueryResultList results = new URIQueryResultList();
        for (SynchronizedVolumePair pair : volumePairs) {
            dbClient.queryByConstraint(getVolumeNativeGuidConstraint(pair.getSourceGUID()), results);
            volumeURIs.add(results.iterator().next());
            dbClient.queryByConstraint(getVolumeNativeGuidConstraint(pair.getTargetGUID()), results);
            volumeURIs.add(results.iterator().next());
        }

        return dbClient.queryObject(Volume.class, volumeURIs);
    }
    
    /**
     * Async Without CG : All SRDF operations will be happen for all volumes available on ra group.
     * Hence we need to change the personalities of the remaining volumes too based on the srdf operation.
     * 
     * This method returns the remaing source volumes list available on the ra group which belongs to given source and target volumes.
     * @param sourceVolume
     * @param targetVolume
     * @return
     */
    public List<Volume> getRemainingSourceVolumesForAsyncRAGroup(Volume sourceVolume, Volume targetVolume){
    	List<Volume> volumeList = new ArrayList<Volume>();
    	
    	if(sourceVolume!=null && targetVolume != null && targetVolume.getSrdfGroup() !=null){
    		RemoteDirectorGroup rdfGroup = dbClient.queryObject(RemoteDirectorGroup.class, targetVolume.getSrdfGroup());
    		if(rdfGroup !=null){
    			StringSet volumeNativeGUIdList = rdfGroup.getVolumes();
    			log.info("volumeNativeGUIdList : {}",volumeNativeGUIdList);
    			if(volumeNativeGUIdList != null){
    				for(String volumeNativeGUId: volumeNativeGUIdList){
    					log.debug("volume nativeGUId:{}",volumeNativeGUId);
    					URIQueryResultList result = new URIQueryResultList();
    					dbClient.queryByConstraint(AlternateIdConstraint.Factory
    							.getVolumeNativeGuidConstraint(volumeNativeGUId), result);
    					Iterator<URI> volumeIterator = result.iterator();
    					if (volumeIterator.hasNext()) {
    						Volume volume = dbClient.queryObject(Volume.class, volumeIterator.next());
    						if(volume != null && PersonalityTypes.SOURCE.toString().equalsIgnoreCase(volume.getPersonality()) &&
    								!volume.getNativeId().equalsIgnoreCase(sourceVolume.getNativeId())){
    							log.info("Found volume {} in vipr db",volume.getNativeGuid());
								volumeList.add(volume);
    						}
    					}
    				}
    			}
    		}
    	}
    	log.info("volume list size {}",volumeList.size());
    	return volumeList;
    }

    /**
     * Given a target volume, this method acquires both the source and target RemoteDirectorGroup instances
     * in order to remove from it the nativeGuid's of the target and its parent.
     *
     * @param target The target volume to be removed from its RemoteDirectorGroup
     */
    public void removeFromRemoteGroups(Volume target) {
        RemoteDirectorGroup tgtGroup = dbClient.queryObject(RemoteDirectorGroup.class, target.getSrdfGroup());
        RemoteDirectorGroup srcGroup = getAssociatedRemoteDirectorGroup(tgtGroup);

        Volume source = dbClient.queryObject(Volume.class, target.getSrdfParent().getURI());
        List<String> nativeGuids = newArrayList(source.getNativeGuid(), target.getNativeGuid());

        removeFromRemoteGroup(tgtGroup, nativeGuids);
        if (srcGroup != null) {
            removeFromRemoteGroup(srcGroup, nativeGuids);
        }
    }

    private RemoteDirectorGroup getAssociatedRemoteDirectorGroup(RemoteDirectorGroup group) {
        URIQueryResultList result = new URIQueryResultList();
        try {
            dbClient.queryByConstraint(getStorageDeviceRemoteGroupsConstraint(group.getRemoteStorageSystemUri()), result);
            return dbClient.queryObject(RemoteDirectorGroup.class, result.iterator().next());
        } catch (Exception e) {
            String msg = String.format("Failed to get associated RemoteDirectorGroup to %s", group.getNativeGuid());
            log.warn(msg, e);
        }
        return null;
    }

    private void removeFromRemoteGroup(RemoteDirectorGroup group, Collection<String> nativeGuids) {
        if (group.getVolumes() != null) {
            for (String nativeGuid : nativeGuids) {
                group.getVolumes().remove(nativeGuid);
            }
            dbClient.persistObject(group);
        }
    }

    private Function<CIMObjectPath, SynchronizedVolumePair> toSynchronizedVolumePairFn() {
        return new Function<CIMObjectPath, SynchronizedVolumePair>() {
            @Override
            public SynchronizedVolumePair apply(CIMObjectPath input) {
                return new SynchronizedVolumePair(input);
            }
        };
    }

    private List<CIMObjectPath> getVolumePathsInRemoteGroup(StorageSystem provider, CIMObjectPath remoteGroupPath) {
        CloseableIterator<CIMObjectPath> volumePaths = null;
        List<CIMObjectPath> result = new ArrayList<>();

        try {
            volumePaths = helper.getAssociatorNames(provider, remoteGroupPath, null, CIM_STORAGE_VOLUME, null, null);
            while (volumePaths.hasNext()) {
                result.add(volumePaths.next());
            }
            return result;
        } catch (WBEMException e) {
            e.printStackTrace();
        } finally {
            if (volumePaths != null) {
                volumePaths.close();
            }
        }

        return Collections.EMPTY_LIST;
    }

    private CIMObjectPath getRemoteGroupPath(StorageSystem provider, CIMObjectPath objectPath) {
        CloseableIterator<CIMObjectPath> names = null;

        try {
            names = helper.getAssociatorNames(provider, objectPath, null, SE_RemoteReplicationCollection, null, null);
            if (names.hasNext()) {
                return names.next();
            }
        } catch (WBEMException e) {
            // TODO Create custom exception
            throw new RuntimeException("Failed to acquire remote replication collection", e);
        } finally {
            if (names != null) {
                names.close();
            }
        }

        throw new RemoteGroupAssociationNotFoundException();
    }

    private CIMObjectPath getStorageSynchronizationFromVolume(StorageSystem provider, CIMObjectPath volumePath) {
        CloseableIterator<CIMObjectPath> references = null;

        try {
            references = helper.getReference(provider, volumePath, CIM_STORAGE_SYNCHRONIZED, null);
            // TODO Could potentially return a local storage synchronized.  Need to make sure we return
            // the correct one!
            if (references.hasNext()) {
                return references.next();
            }
        } catch (WBEMException e) {
            throw new RuntimeException("Failed to acquire storage synchronization", e);
        } finally {
            if (references != null) {
                references.close();
            }
        }

        throw new RuntimeException("Failed to acquire storage synchronization");
    }

    private Collection<CIMObjectPath> getConsistencyGroupSyncPairs(StorageSystem sourceSystem, Volume source,
                                                                   StorageSystem targetSystem, Volume target,
                                                                   StorageSystem activeProviderSystem) throws WBEMException {
        List<URI> srcVolumeUris =   dbClient.queryByConstraint(getVolumesByConsistencyGroup(source.getConsistencyGroup()));
        List<Volume> cgSrcVolumes = dbClient.queryObject(Volume.class, srcVolumeUris);
        Collection<String> srcDevIds = transform(cgSrcVolumes, fctnBlockObjectToNativeID());

        List<URI> tgtVolumeUris =   dbClient.queryByConstraint(getVolumesByConsistencyGroup(target.getConsistencyGroup()));
        List<Volume> cgTgtVolumes = dbClient.queryObject(Volume.class, tgtVolumeUris);
        Collection<String> tgtDevIds = transform(cgTgtVolumes, fctnBlockObjectToNativeID());
        
        // Get the storagesync instances for remote sync/async mirrors
        List<CIMObjectPath> repPaths = helper.getReplicationRelationships(activeProviderSystem,
                REMOTE_LOCALITY_VALUE, MIRROR_VALUE, SRDFOperations.Mode.valueOf(target.getSrdfCopyMode()).getMode(),
                STORAGE_SYNCHRONIZED_VALUE);

        log.info("Found {} relationships", repPaths.size());
        log.info("Looking for System elements on {} with IDs {}", sourceSystem.getNativeGuid(),
                Joiner.on(',').join(srcDevIds));
        log.info("Looking for Synced elements on {} with IDs {}", targetSystem.getNativeGuid(),
                Joiner.on(',').join(tgtDevIds));
        return filter(repPaths, and(
                cgSyncPairsPredicate(sourceSystem.getNativeGuid(), srcDevIds, CP_SYSTEM_ELEMENT),
                cgSyncPairsPredicate(targetSystem.getNativeGuid(), tgtDevIds, CP_SYNCED_ELEMENT)));
    }

    private Predicate<CIMObjectPath> cgSyncPairsPredicate(final String systemNativeGuid, final Collection<String> nativeIds,
                                                          final String propertyName) {
        return new Predicate<CIMObjectPath>() {
            @Override
            public boolean apply(CIMObjectPath path) {
                String el = path.getKeyValue(propertyName).toString();
                CIMObjectPath elPath = new CIMObjectPath(el);
                String elDevId   = elPath.getKeyValue(CP_DEVICE_ID).toString();
                String elSysName = elPath.getKeyValue(CP_SYSTEM_NAME).toString().
                        replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);

                return elSysName.equalsIgnoreCase(systemNativeGuid) && nativeIds.contains(elDevId);
            }
        };
    }

    private Predicate<CIMObjectPath> activeLinkPredicate(final StorageSystem provider) {
        return new Predicate<CIMObjectPath>() {
            @Override
            public boolean apply(CIMObjectPath syncPath) {
                try {
                    CIMInstance syncInstance = getInstance(syncPath, provider);
                    return !isBroken(syncInstance); // Not broken, so add to the "not paused" collection
                } catch (Exception e) {
                    log.warn("Failed to determine synchronization state for {}", syncPath, e);
                    return false;
                }
            }
        };
    }
}
