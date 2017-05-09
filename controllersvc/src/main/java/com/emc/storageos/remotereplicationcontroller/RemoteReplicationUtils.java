package com.emc.storageos.remotereplicationcontroller;


import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByAltId;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByRelation;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesUriByAltId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationController.RemoteReplicationOperations;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationDataClient;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationDataClientImpl;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.emc.storageos.volumecontroller.impl.utils.ObjectLocalCache;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.NeighborhoodsMatcher;

public class RemoteReplicationUtils {
    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationUtils.class);
    public static List<URI> getElements(DbClient dbClient, List<URI> remoteReplicationPairs) {

        List<RemoteReplicationPair> systemReplicationPairs = dbClient.queryObject(RemoteReplicationPair.class, remoteReplicationPairs);
        List<URI> volumes = new ArrayList<>();
        for(RemoteReplicationPair pair : systemReplicationPairs) {
            URI sourceVolume = pair.getSourceElement().getURI();
            URI targetVolume = pair.getTargetElement().getURI();
            volumes.add(sourceVolume);
            volumes.add(targetVolume);
        }
        return volumes;
    }

    /**
     * Get storage systems which contain storage pool(s) belonging to both the
     * given vArray and vPool.
     *
     * @param varrayURI
     * @param vpoolURI
     * @return URI string set of qualified storage systems
     */
    public static Set<String> getStorageSystemsForVarrayVpool(URI varrayURI, URI vpoolURI, DbClient dbClient,
            CoordinatorClient coordinator) {
        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolURI);
        List<StoragePool> vpoolPools = VirtualPool.getValidStoragePools(vpool, dbClient, true);
        NeighborhoodsMatcher matcher = new NeighborhoodsMatcher();
        matcher.setCoordinatorClient(coordinator);
        matcher.setObjectCache(new ObjectLocalCache(dbClient));
        Set<URI> varrayPools = new HashSet<>(matcher.getVarrayPools(varrayURI.toString()));
        Set<String> result = new HashSet<>();
        for (StoragePool pool : vpoolPools) {
            if (varrayPools.contains(pool.getId())) {
                result.add(pool.getStorageDevice().toString());
            }
        }
        return result;
    }

    public static VirtualPool getTargetVPool(VirtualPool remoteReplicationVPool, DbClient dbClient) {
        StringMap remoteReplicationSettings = remoteReplicationVPool.getRemoteReplicationProtectionSettings();
        VirtualPool targetVirtualPool = remoteReplicationVPool;

        // There can be multiple entries for target varray and vpool combinations in the vpool
        for (Map.Entry<String, String> entry : remoteReplicationSettings.entrySet()) {
            String targetVirtualPoolId = entry.getValue();
            if (targetVirtualPoolId != null) {
                targetVirtualPool = dbClient.queryObject(VirtualPool.class, URIUtil.uri(targetVirtualPoolId));
            }
            break;
        }
        return targetVirtualPool;
    }

    public static VirtualArray getTargetVArray(VirtualPool remoteReplicationVPool, DbClient dbClient) {
        StringMap remoteReplicationSettings = remoteReplicationVPool.getRemoteReplicationProtectionSettings();
        VirtualArray targetVirtualArray = null;

        // There can be multiple entries for target varray and vpool combinations in the vpool
        for (Map.Entry<String, String> entry : remoteReplicationSettings.entrySet()) {
            String targetVirtualArrayId = entry.getKey();
            targetVirtualArray = dbClient.queryObject(VirtualArray.class, URIUtil.uri(targetVirtualArrayId));
            break;
        }
        return targetVirtualArray;
    }

    /**
     * Return RemoteReplicaionSet objects for a given storage system.
     *
     * @param storageSystem storage system instance
     * @param dbClient  database client
     * @return
     */
    public static List<RemoteReplicationSet> getRemoteReplicationSetsForStorageSystem(StorageSystem storageSystem, DbClient dbClient) {
        String storageSystemType = storageSystem.getSystemType();
        List<RemoteReplicationSet> systemSets = new ArrayList<>();

        // get existing replication sets for the storage system type
        List<URI> remoteReplicationSets =
                queryActiveResourcesUriByAltId(dbClient, RemoteReplicationSet.class,
                        "storageSystemType", storageSystemType);

        for (URI setUri : remoteReplicationSets) {
            RemoteReplicationSet systemSet = dbClient.queryObject(RemoteReplicationSet.class, setUri);
            if(systemSet.getSystemToRolesMap().keySet().contains(storageSystem.getId().toString())) {
                systemSets.add(systemSet);
            }
        }
        return systemSets;
    }


    public static List<RemoteReplicationPair> getRemoteReplicationPairsForSourceElement(URI sourceElementURI, DbClient dbClient) {
        _log.info("Called: getRemoteReplicationPairsForSourceElement() for storage element {}", sourceElementURI);

        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair> rrPairs =
                queryActiveResourcesByRelation(dbClient, sourceElementURI, RemoteReplicationPair.class,
                        "sourceElement");

        _log.info("Found pairs: {}", rrPairs);

        return rrPairs;
    }

    public static List<RemoteReplicationPair> getRemoteReplicationPairsForTargetElement(URI targetElementURI, DbClient dbClient) {
        _log.info("Called: getRemoteReplicationPairsForTargetElement() for storage element {}", targetElementURI);

        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair> rrPairs =
                queryActiveResourcesByRelation(dbClient, targetElementURI, RemoteReplicationPair.class,
                        "targetElement");

        _log.info("Found pairs: {}", rrPairs);

        return rrPairs;
    }

    public static List<RemoteReplicationPair> getRemoteReplicationPairsForCG(BlockConsistencyGroup cg, DbClient dbClient) {
        _log.info("Called: getRemoteReplicationPairsForCG() for consistency group {}", cg.getId());
        List<RemoteReplicationPair> rrPairs = new ArrayList<>();
        List<Volume> cgVolumes = CustomQueryUtility.queryActiveResourcesByRelation(dbClient, cg.getId(),
                Volume.class, "consistencyGroup");

        // get all remote replication pairs where these volumes are source volumes
        for (Volume volume : cgVolumes) {
           rrPairs.addAll(getRemoteReplicationPairsForSourceElement(volume.getId(), dbClient));
           rrPairs.addAll(getRemoteReplicationPairsForTargetElement(volume.getId(), dbClient));
        }
        return rrPairs;
    }

    public static List<RemoteReplicationPair> getRemoteReplicationPairsForSourceCG(BlockConsistencyGroup cg, DbClient dbClient) {
        _log.info("Called: getRemoteReplicationPairsForSourceCG() for consistency group {}", cg.getId());
        List<RemoteReplicationPair> rrPairs = new ArrayList<>();
        List<Volume> cgVolumes = CustomQueryUtility.queryActiveResourcesByRelation(dbClient, cg.getId(),
                Volume.class, "consistencyGroup");

        // get all remote replication pairs where these volumes are source volumes
        for (Volume volume : cgVolumes) {
           rrPairs.addAll(getRemoteReplicationPairsForSourceElement(volume.getId(), dbClient));
        }
        return rrPairs;
    }


    /**
     * Validate that remote replication link operation is valid based on the remote replication configuration discovered on the device.
     *
     * @param rrElement remote replication element (pair, cg, group, set)
     * @param operation operation
     * @return true/false
     */
    public static void validateRemoteReplicationOperation(DbClient dbClient, RemoteReplicationElement rrElement,
            RemoteReplicationOperations operation) {
        boolean isOperationValid = true;
        // todo: validate that this operation is valid (operational validity):
        //   For rr pairs:
        //     parent set supports operations on pairs;
        //     if pair is in a group, check that group consistency is not enforced (operations are allowed on subset of pairs);
        //     if pair has volumes in consistency groups, this is invalid --- no operations on individual pairs in consistency groups
        //   For rr cgs:
        //     parent set supports operations on pairs;
        //     if pairs are in groups, check that group consistency is not enforced (operations are allowed on subset of pairs);
        //   For groups:
        //     parent set supports operations on groups;
        //   For sets:
        //     set supports operations on sets;
        switch (rrElement.getType()) {
            case REPLICATION_PAIR:
                RemoteReplicationPair rrPair = dbClient.queryObject(RemoteReplicationPair.class, rrElement.getElementUri());
                isOperationValid = supportOperationOnRrPair(rrPair, dbClient);
                break;
            case CONSISTENCY_GROUP:
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, rrElement.getElementUri());
                List<RemoteReplicationPair> rrPairs = getRemoteReplicationPairsForCG(cg, dbClient);
                for (RemoteReplicationPair pair : rrPairs) {
                    if (!supportOperationOnRrCGPair(pair, dbClient)) {
                        isOperationValid = false;
                        break;
                    }
                }
                break;
            case REPLICATION_GROUP:
                RemoteReplicationGroup rrGroup = dbClient.queryObject(RemoteReplicationGroup.class, rrElement.getElementUri());
                RemoteReplicationSet rrSet = getRemoteReplicationSetForRrGroup(dbClient, rrGroup);
                isOperationValid = (rrSet != null && rrSet.supportRemoteReplicationGroupOperation());
                break;
            case REPLICATION_SET:
                rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrElement.getElementUri());
                isOperationValid = rrSet.supportRemoteReplicationSetOperation();
                break;
        }

        if (!isOperationValid) { // bad request
            throw APIException.badRequests.remoteReplicationLinkOperationIsNotAllowed(rrElement.getType().toString(),
                    rrElement.getElementUri().toString(), operation.toString());
        }
    }

    /**
     * Find a remote replication set that meets below constraints:
     *  1. has the same storage system type with the given remote replication group;
     *  2. contains both the source and target systems of the remote replication group.
     */
    private static RemoteReplicationSet getRemoteReplicationSetForRrGroup(DbClient dbClient, RemoteReplicationGroup rrGroup) {
        List<RemoteReplicationSet> rrSets =
                queryActiveResourcesByAltId(dbClient, RemoteReplicationSet.class, "storageSystemType", rrGroup.getStorageSystemType());
        for (RemoteReplicationSet rrSet : rrSets) {
            if (rrSet.getSystemToRolesMap().containsKey(rrGroup.getSourceSystem().toString())
                    && rrSet.getSystemToRolesMap().containsKey(rrGroup.getTargetSystem().toString())) {
                return rrSet;
            }
        }
        // should not run here because there's must be a rr set for a rr group
        return null;
    }

    /**
     * @return true if rr pair is in cg and if rr set of given rr pair support rr pair granularity
     *         operation, and if this rr pair is in a rr group, the rr group
     *         should not enforce group consistency, which means it allows
     *         operations on subset of pairs
     */
    private static boolean supportOperationOnRrCGPair(RemoteReplicationPair rrPair, DbClient dbClient) {
        if (!rrPair.isInCG(dbClient)) {
            _log.info("RR pair {} has source/target elements outside of consistency group.", rrPair.getNativeId());
            return false;
        }

        RemoteReplicationSet rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrPair.getReplicationSet());
        if (!rrSet.supportRemoteReplicationPairOperation()) {
            return false;
        }

        if (!rrPair.isGroupPair()) {
            return true;
        }
        RemoteReplicationGroup rrGroup = dbClient.queryObject(RemoteReplicationGroup.class, rrPair.getReplicationGroup());
        if (rrGroup.getIsGroupConsistencyEnforced() == Boolean.TRUE) {
            // No pair operation is allowed if consistency is to be enforced on group level
            return false;
        }
        return true;
    }

    /**
     * @return true if rr set of given rr pair support rr pair granularity
     *         operation, and if pair is not in CG, and if this rr pair is in a rr group, the rr group
     *         should not enforce group consistency, which means it allows
     *         operations on subset of pairs
     */
    private static boolean supportOperationOnRrPair(RemoteReplicationPair rrPair, DbClient dbClient) {
        RemoteReplicationSet rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrPair.getReplicationSet());
        if (!rrSet.supportRemoteReplicationPairOperation()) {
            return false;
        }
        if (rrPair.isInCG(dbClient)) {
            _log.info("RR pair {} has source/target elements in CG.", rrPair.getNativeId());
            return false;
        }
        if (!rrPair.isGroupPair()) {
            return true;
        }
        RemoteReplicationGroup rrGroup = dbClient.queryObject(RemoteReplicationGroup.class, rrPair.getReplicationGroup());
        if (rrGroup.getIsGroupConsistencyEnforced() == Boolean.TRUE) {
            // No pair operation is allowed if consistency is to be enforced on group level
            return false;
        }
        return true;
    }


    public static void validateRemoteReplicationModeChange(DbClient dbClient, RemoteReplicationElement rrElement, String newMode) {

        // todo: validate that this operation is valid:
        //   For rr pair and cgs:
        //       validate that parent set supports operations on pairs;
        //       validate that pair is not in rr group;
        //       validate that set supports new replication mode;
        //       check that set/group parents are reachable
        //   For rr group:
        //       check if group is reachable;
        //       validate that parent set supports operation on groups;
        //       validate that parent set supports new replication mode;
        //   For rr set:
        //       check id set is reachable;
        //       validate that set supports operations on sets;
        //       validate that set supports new replication mode;
        //
        boolean isChangeValid = true;
        switch (rrElement.getType()) {
            case REPLICATION_PAIR:
                RemoteReplicationPair rrPair = checkDataObjectExists(RemoteReplicationPair.class,
                        rrElement.getElementUri(), dbClient);
                isChangeValid = supportModeChangeOnRrPair(rrPair, dbClient, newMode);
                break;
            case CONSISTENCY_GROUP:
                BlockConsistencyGroup cg = checkDataObjectExists(BlockConsistencyGroup.class,
                        rrElement.getElementUri(), dbClient);
                List<RemoteReplicationPair> rrPairs = getRemoteReplicationPairsForCG(cg, dbClient);
                isChangeValid = supportModeChangeOnAllRrPairs(rrPairs, dbClient, newMode);
                break;
            case REPLICATION_GROUP:
                RemoteReplicationGroup rrGroup = checkDataObjectExists(RemoteReplicationGroup.class,
                        rrElement.getElementUri(), dbClient);
                if (rrGroup.getReachable() != Boolean.TRUE) {
                    isChangeValid = false;
                    break;
                }
                RemoteReplicationSet rrSet = getRemoteReplicationSetForRrGroup(dbClient, rrGroup);
                isChangeValid = rrSet.supportRemoteReplicationGroupOperation() && rrSet.supportMode(newMode);
                break;
            case REPLICATION_SET:
                rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrElement.getElementUri());
                isChangeValid = rrSet.getReachable() == Boolean.TRUE && rrSet.supportRemoteReplicationSetOperation()
                        && rrSet.supportMode(newMode);
                break;
        }
        if (!isChangeValid) {
            throw APIException.badRequests.remoteReplicationModeChangeIsNotAllowed(rrElement.getType().toString().toLowerCase(),
                    rrElement.getElementUri().toString(), newMode);
        }
    }

    private static <T extends DataObject> T checkDataObjectExists(Class<T> clazz, URI uri, DbClient dbClient) {
        T result = dbClient.queryObject(clazz, uri);
        if (result == null) {
            throw APIException.badRequests.dataObjectNotExists(clazz.getSimpleName(), uri);
        }
        return result;
    }

    private static boolean supportModeChangeOnRrPair(RemoteReplicationPair rrPair, DbClient dbClient, String newMode) {
        RemoteReplicationSet rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrPair.getReplicationSet());
        return rrSet.getReachable() == Boolean.TRUE && rrSet.supportRemoteReplicationPairOperation()
                && !rrPair.isGroupPair() && rrSet.supportMode(newMode);
    }

    private static boolean supportModeChangeOnAllRrPairs(Collection<RemoteReplicationPair> rrPairs, DbClient dbClient, String newMode) {
        for (RemoteReplicationPair rrPair : rrPairs) {
            RemoteReplicationSet rrSet = dbClient.queryObject(RemoteReplicationSet.class, rrPair.getReplicationSet());
            if (rrSet.getReachable() != Boolean.TRUE || !rrSet.supportRemoteReplicationPairOperation()
                    || rrPair.isGroupPair() || !rrSet.supportMode(newMode)) {
                return false;
            }
        }
        return true;
    }

    public static Iterator<RemoteReplicationSet> findAllRemoteRepliationSetsIteratively(DbClient dbClient) {
        List<URI> ids = dbClient.queryByType(RemoteReplicationSet.class, true);
        _log.info("Found sets: {}", ids);
        return dbClient.queryIterativeObjects(RemoteReplicationSet.class, ids);
    }

    public static Iterator<RemoteReplicationGroup> findAllRemoteRepliationGroupsIteratively(DbClient dbClient) {
        List<URI> ids = dbClient.queryByType(RemoteReplicationGroup.class, true);
        _log.info("Found groups: {}", ids);
        return dbClient.queryIterativeObjects(RemoteReplicationGroup.class, ids);
    }

    public static List<RemoteReplicationPair> findAllRemoteRepliationPairsByRrSet(URI rrSetUri, DbClient dbClient) {
        List<RemoteReplicationPair> result = new ArrayList<RemoteReplicationPair>();
        QueryResultList<URI> uriList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getRemoteReplicationPairSetConstraint(rrSetUri), uriList);
        for (URI uri : uriList) {
            result.add(dbClient.queryObject(RemoteReplicationPair.class, uri));
        }
        return result;
    }

    public static List<RemoteReplicationPair> findAllRemoteRepliationPairsByRrGroup(URI rrGroupUri, DbClient dbClient) {
        List<RemoteReplicationPair> result = new ArrayList<RemoteReplicationPair>();
        QueryResultList<URI> uriList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getRemoteReplicationPairGroupConstraint(rrGroupUri), uriList);
        for (URI uri : uriList) {
            result.add(dbClient.queryObject(RemoteReplicationPair.class, uri));
        }
        return result;
    }

    /**
     * Create remote replication pair for srdf volume pair.
     *
     * @param argSourceUri srdf source volume
     * @param argTargetUri srdf target volume
     */
    public static void createRemoteReplicationPairForSrdfPair(URI argSourceUri, URI argTargetUri, DbClient dbClient) {

        try {
            URI sourceUri = argSourceUri;
            URI targetUri = argTargetUri;
            boolean swapped = isSwapped(argSourceUri, dbClient);
            if (swapped) {
                sourceUri = argTargetUri;
                targetUri = argSourceUri;
            }
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair driverRrPair = null;
            driverRrPair = buildRemoteReplicationPairForSrdfPair(sourceUri, targetUri, swapped, dbClient);
            RemoteReplicationDataClient remoteReplicationDataClient = new RemoteReplicationDataClientImpl(dbClient);
            remoteReplicationDataClient.createRemoteReplicationPair(driverRrPair, sourceUri, targetUri);
        } catch (Exception ex) {
            String msg = String.format("Failed to create remote replication pair for srdf pair: %s -> %s", argSourceUri, argTargetUri);
            _log.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }

    /**
     * Delete remote replication pairs for all volumes in the same CG as source volume.
     *
     * @param argSourceUri srdf source volume  URI
     * @param argTargetUri srdf target volume URI
     * @param dbClient
     */
    public static void deleteRemoteReplicationPairsForSrdfCG(URI argSourceUri, URI argTargetUri, DbClient dbClient) {
        Volume sourceVolume = dbClient.queryObject(Volume.class, argSourceUri);
        if (sourceVolume == null) {
            String msg = String.format("Source volume could not be found in the ViPR database: %s", argSourceUri);
            _log.error(msg);
            throw new RuntimeException(msg);
        }
        BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, sourceVolume.getConsistencyGroup());
        if (cg == null) {
            String msg = String.format("Source volume %s is not in CG .", argSourceUri);
            _log.warn(msg);
            deleteRemoteReplicationPairForSrdfPair(argSourceUri, argTargetUri, dbClient);
        } else {
            List<Volume> cgVolumes = CustomQueryUtility.queryActiveResourcesByRelation(dbClient, cg.getId(),
                    Volume.class, "consistencyGroup");
            for (Volume cgVolume : cgVolumes) {
                Volume target = SRDFUtils.getFirstTarget(cgVolume, dbClient);
                deleteRemoteReplicationPairForSrdfPair(cgVolume.getId(), target.getId(), dbClient);
            }
        }
    }

    /**
     * Delete remote replication pair for srdf volume pair.
     *
     * @param argSourceUri srdf source volume
     * @param argTargetUri srdf target volume
     */
    public static void deleteRemoteReplicationPairForSrdfPair(URI argSourceUri, URI argTargetUri, DbClient dbClient) {
        String sourceLabel = null;
        String targetLabel = null;
        try {
            URI sourceUri = argSourceUri;
            URI targetUri = argTargetUri;
            if (isSwapped(argSourceUri, dbClient)) {
                sourceUri = argTargetUri;
                targetUri = argSourceUri;
            }
            Volume source = dbClient.queryObject(Volume.class, sourceUri);
            Volume target = dbClient.queryObject(Volume.class, targetUri);
            if (source == null) {
                _log.warn("Srdf source volume does not exist in database. ID: {}", sourceUri);
            } else {
                sourceLabel = source.getLabel();
            }
            if (target == null) {
                _log.warn("Srdf target volume does not exist in database. ID: {}", targetUri);
            } else {
                targetLabel = target.getLabel();
            }
            _log.info(String.format("Processing srdf pair: %s/%s -> %s/%s", sourceLabel, sourceUri, targetLabel, targetUri));

            RemoteReplicationDataClient remoteReplicationDataClient = new RemoteReplicationDataClientImpl(dbClient);
            remoteReplicationDataClient.deleteRemoteReplicationPair(sourceUri, targetUri);
        } catch (Exception ex) {
            String msg = String.format("Failed to delete remote replication pair for srdf pair: %s/%s -> %s/%s",
                    sourceLabel, argSourceUri, targetLabel, argTargetUri);
            _log.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }

    /**
     * Builds sb sdk remote replication pair for srdf source and target volumes.
     *
     * @param inSourceUri
     * @param inTargetUri
     * @param dbClient
     * @return sb sdk remote replication pair
     */
    private static com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair
       buildRemoteReplicationPairForSrdfPair(URI inSourceUri, URI inTargetUri, boolean swapped, DbClient dbClient) {
        URI sourceUri = inSourceUri;
        URI targetUri = inTargetUri;
        try {
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair
                    driverRrPair = new com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair();
            Volume source = dbClient.queryObject(Volume.class, sourceUri);
            Volume target = dbClient.queryObject(Volume.class, targetUri);
            
            _log.info(String.format("Processing srdf pair: %s/%s -> %s/%s", source.getLabel(), sourceUri, target.getLabel(), targetUri));
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, source.getStorageController());
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, target.getStorageController());

            URI rdfGroupId = target.getSrdfGroup();
            String replicationMode = target.getSrdfCopyMode();
            String replicationDirection = SRDFUtils.SyncDirection.SOURCE_TO_TARGET.toString();
            if (swapped) {
                replicationDirection = SRDFUtils.SyncDirection.TARGET_TO_SOURCE.toString();
                rdfGroupId = source.getSrdfGroup();
                replicationMode = source.getSrdfCopyMode();
            }
            RemoteDirectorGroup rdGroup = dbClient.queryObject(RemoteDirectorGroup.class, rdfGroupId);

            // Get native id for rr set.
            List<StorageSystem> storageSystems = Arrays.asList(sourceSystem, targetSystem);
            String rrSetNativeId = getRemoteReplicationSetNativeIdForSrdfSet(storageSystems);

            // Get nativeId for rr group.
            String rrGroupNativeId = null;
            if (rdGroup != null) {
                rrGroupNativeId = getRemoteReplicationGroupNativeIdForSrdfGroup(sourceSystem, targetSystem, rdGroup);
            } else {
                _log.info("RDF group is not defined for srdf pair: {} -> {}", sourceUri, targetUri);
            }

            StorageVolume driverSourceVolume = new StorageVolume();
            driverSourceVolume.setStorageSystemId(sourceSystem.getSerialNumber());
            driverSourceVolume.setNativeId(source.getNativeId());
            //
            StorageVolume driverTargetVolume = new StorageVolume();
            driverTargetVolume.setStorageSystemId(targetSystem.getSerialNumber());
            driverTargetVolume.setNativeId(target.getNativeId());

            driverRrPair.setNativeId(getRemoteReplicationPairNativeIdForSrdfPair(source, target));
            driverRrPair.setReplicationSetNativeId(rrSetNativeId);
            driverRrPair.setReplicationGroupNativeId(rrGroupNativeId);
            driverRrPair.setSourceVolume(driverSourceVolume);
            driverRrPair.setTargetVolume(driverTargetVolume);
            driverRrPair.setReplicationMode(replicationMode);
            driverRrPair.setReplicationState(source.getLinkStatus());
            driverRrPair.setReplicationDirection(replicationDirection);

            return driverRrPair;
        } catch (Exception ex) {
            String msg = String.format("Failed to build remote replication pair for srdf pair: %s -> %s", sourceUri, targetUri);
            _log.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }
    
    public static String getRemoteReplicationGroupNativeIdForSrdfGroup(StorageSystem sourceSystem, StorageSystem targetSystem,
                                                                      RemoteDirectorGroup rdGroup) {
        return sourceSystem.getSerialNumber() + Constants.PLUS + rdGroup.getSourceGroupId() + Constants.PLUS
                + targetSystem.getSerialNumber() + Constants.PLUS + rdGroup.getRemoteGroupId();
    }


    /**
     * Return native id of remote replication set based on srdf set.
     * @param storageSystems storage systems in the set
     * @return native id
     */
    public static String getRemoteReplicationSetNativeIdForSrdfSet(List<StorageSystem> storageSystems) {
        if (storageSystems == null || storageSystems.isEmpty()) {
            return null;
        }
        
        List<String> systemSerialNumbers = new ArrayList<>();
        for (StorageSystem system : storageSystems) {
            systemSerialNumbers.add(system.getSerialNumber());
        }
        Collections.sort(systemSerialNumbers);
        return StringUtils.join(systemSerialNumbers, "+");
    }

    public static String getRemoteReplicationPairNativeIdForSrdfPair(Volume source, Volume target) {
        return source.getNativeId()+ Constants.PLUS + target.getNativeId();

    }
    
    /**
     * checks for the existence of the remote replication pair and updates or creates it as needed
     * 
     * @param argSourceUri
     * @param argTargetUri
     * @param dbClient
     */
    public static void updateOrCreateReplicationPairForSrdfPair(URI argSourceUri, URI argTargetUri, DbClient dbClient) {
        try {
            URI sourceUri = argSourceUri;
            URI targetUri = argTargetUri;
            // NOTE: SRDF volume roles may not correspond to volume roles in remote replication pair.
            // This is due to the fact that SRDF swap operation changes source and target volumes, but
            // remote replication pair roles are immutable and swap operation changes replication direction
            // property.
            boolean swapped = isSwapped(argSourceUri, dbClient);
            if (swapped) {
                sourceUri = argTargetUri;
                targetUri = argSourceUri;
            }
            RemoteReplicationDataClient remoteReplicationDataClient = new RemoteReplicationDataClientImpl(dbClient);
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair driverRrPair = buildRemoteReplicationPairForSrdfPair(sourceUri, targetUri, swapped, dbClient);
            if (null == remoteReplicationDataClient.checkRemoteReplicationPairExistsInDB(sourceUri, targetUri)) {
                remoteReplicationDataClient.createRemoteReplicationPair(driverRrPair, sourceUri, targetUri);
            } else {
                remoteReplicationDataClient.updateRemoteReplicationPair(driverRrPair, sourceUri, targetUri);
            }
        } catch (Exception e) {
            String msg = String.format("Failed to update or create remote replication pair for srdf pair: %s -> %s", argSourceUri, argTargetUri);
            _log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    /**
     * updates properties of a RemoteReplicationPairobject for an SRDF source and target
     * 
     * @param argSourceUri
     * @param argTargetUri
     * @param dbClient
     */
    public static void updateRemoteReplicationPairForSrdfPair(URI argSourceUri, URI argTargetUri, DbClient dbClient) {
        try {
            URI sourceUri = argSourceUri;
            URI targetUri = argTargetUri;
            // NOTE: SRDF volume roles may not correspond to volume roles in remote replication pair.
            // This is due to the fact that SRDF swap operation changes source and target volumes, but
            // remote replication pair roles are immutable and swap operation changes replication direction
            // property.
            boolean swapped = isSwapped(argSourceUri, dbClient);
            if (swapped) {
                sourceUri = argTargetUri;
                targetUri = argSourceUri;
            }
            RemoteReplicationDataClient remoteReplicationDataClient = new RemoteReplicationDataClientImpl(dbClient);
            com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair driverRrPair = buildRemoteReplicationPairForSrdfPair(sourceUri, targetUri, swapped, dbClient);
            remoteReplicationDataClient.updateRemoteReplicationPair(driverRrPair, sourceUri, targetUri);
        } catch (Exception ex) {
            String msg = String.format("Failed to update remote replication pair for srdf pair: %s -> %s", argSourceUri, argTargetUri);
            _log.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }
    
    /**
     * determines if the source and target srdf pair are in a swapped state based on the source volume virtual pool
     * 
     * @param sourceVolumeId
     * @param dbClient
     * @return
     */
    private static boolean isSwapped(URI sourceVolumeId, DbClient dbClient) {
        List<Volume> sourceVolume = dbClient.queryObjectField(Volume.class, "virtualPool", Arrays.asList(sourceVolumeId));
        if (sourceVolume == null || sourceVolume.isEmpty()) {
            String msg = String.format("Source volume could not be found in the ViPR database: %s", sourceVolumeId);
            _log.error(msg);
            throw new RuntimeException(msg);
        }
        
        List<VirtualPool> vpools = dbClient.queryObjectField(VirtualPool.class, "remoteProtectionSettings", Arrays.asList(sourceVolume.iterator().next().getVirtualPool()));
        return (vpools == null || vpools.isEmpty());
    }


    static public boolean isSwapped(RemoteReplicationPair systemPair, DbClient dbClient) {

        boolean isSwapped = false;
        URI sourceVolumeId = systemPair.getSourceElement().getURI();
        List<Volume> sourceVolume = dbClient.queryObjectField(Volume.class, "personality", Arrays.asList(sourceVolumeId));
        if (sourceVolume == null || sourceVolume.isEmpty()) {
            String msg = String.format("Source volume could not be found in the ViPR database: %s", sourceVolumeId);
            _log.error(msg);
            throw new RuntimeException(msg);
        }
        if (Volume.PersonalityTypes.TARGET.name().equalsIgnoreCase(sourceVolume.get(0).getPersonality())) {
            isSwapped = true;
        }
        return isSwapped;
    }

}
