/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getBlockSnapshotByConsistencyGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.cinder.model.CinderAvailabiltyZone;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class CinderHelpers {
    private DbClient _dbClient;
    private PermissionsHelper _permissionsHelper;
    private static final Logger _log = LoggerFactory.getLogger(CinderHelpers.class);
    private static final long GB = 1024 * 1024 * 1024;
    private static CinderHelpers instCinderHelpers = null;
    private static Object mutex = new Object();

    private CinderHelpers(DbClient dbClient, PermissionsHelper permissionsHelper) {
        _dbClient = dbClient;
        _permissionsHelper = permissionsHelper;
    }

    public CinderHelpers() {
    }

    
    public static CinderHelpers getInstance(DbClient dbClient, PermissionsHelper permissionsHelper) {

        if (instCinderHelpers == null) {
            synchronized (mutex) {
                if (instCinderHelpers == null) {
                    instCinderHelpers = new CinderHelpers(dbClient, permissionsHelper);
                }
            }
        }
        return instCinderHelpers;
    }
        
    
    /**
     * Get project from the OpenStack Tenant ID parameter
     * 
     * 
     * @prereq none
     * 
     * @param openstackTenantId
     * @param user - with user credential details
     * 
     * @brief get project fro given tenant_id
     * @return Project
     */
    protected Project getProject(String openstackTenantId, StorageOSUser user) {
        URI vipr_tenantId = URI.create(user.getTenantId());
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(
                PrefixConstraint.Factory.getTagsPrefixConstraint(
                        Project.class, openstackTenantId, vipr_tenantId),
                uris);
        for (URI projectUri : uris) {
            Project project = _dbClient.queryObject(Project.class, projectUri);
            if (project != null && isAuthorized(projectUri, user))
                return project;
            else if (!isAuthorized(projectUri, user)) {
                throw APIException.badRequests.accessDenied();
            }
        }
        return null;  // no project found
    }

    
    // Helper function to check if the user has authorization to access the project
    // This is used by all search functions
    private boolean isAuthorized(URI projectUri, StorageOSUser user) {
        if (_permissionsHelper == null)
            return false;
        Project project = _permissionsHelper.getObjectById(projectUri, Project.class);
        if (project == null)
            return false;
        if ((_permissionsHelper.userHasGivenRole(user, project.getTenantOrg().getURI(),
                Role.SYSTEM_MONITOR, Role.TENANT_ADMIN) || _permissionsHelper.userHasGivenACL(user,
                projectUri, ACL.ANY))) {
            return true;
        }
        else
            return false;
    }

    /**
     * Get vpool from the given label
     * 
     * 
     * @prereq none
     * 
     * @param vpool_name
     * 
     * @brief get vpool
     * @return vpool
     */
    public VirtualPool getVpool(String vpoolName) {
        if (vpoolName == null)
            return null;
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(
                PrefixConstraint.Factory.getLabelPrefixConstraint(
                        VirtualPool.class, vpoolName),
                uris);
        for (URI vpoolUri : uris) {
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, vpoolUri);
            if (vpool != null && vpool.getType().equals(VirtualPool.Type.block.name()))
                return vpool;
        }
        return null;  // no matching vpool found
    }

    /**
     * Get varray from the given label
     * 
     * 
     * @prereq none
     * 
     * @param varray_name
     * @param user
     * 
     * @brief get varray
     * @return varray
     */
    public VirtualArray getVarray(String varrayName, StorageOSUser user) {
        if ((varrayName == null) || (varrayName.equals(""))) {
            ArrayList<CinderAvailabiltyZone> azList = new ArrayList<CinderAvailabiltyZone>();
            getAvailabilityZones(azList, user);

            if (!azList.isEmpty()) {
                varrayName = ((CinderAvailabiltyZone) azList.get(0)).zoneName;
            }
            else {
                throw APIException.internalServerErrors.genericApisvcError("Get Varray failed", new Throwable("VArray not configured."));
            }
        }

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(
                PrefixConstraint.Factory.getLabelPrefixConstraint(
                        VirtualArray.class, varrayName),
                uris);
        if (uris != null) {
            while (uris.iterator().hasNext()) {
                URI varrayUri = uris.iterator().next();
                VirtualArray varray = _dbClient.queryObject(VirtualArray.class, varrayUri);
                if (varray != null && !varray.getInactive())
                    return varray;
            }
        }
        return null;  // no matching varray found
    }

    public String trimId(String id) {
        String[] splits = id.split(":");
        if (splits.length >= 4) {
            String uriId = splits[3];
            return uriId;
        }
        else {
            return null;
        }
    }

    /**
     * Get list of availability zones
     * 
     * 
     * @prereq none
     * 
     * @param az_list
     * @param user
     * 
     * @brief get availablityzones
     * @return availablityZoneList
     */
    public List getAvailabilityZones(List azList, StorageOSUser user) {
        _log.debug("retrieving virtual arrays via dbclient");

        List<VirtualArray> nhObjList = Collections.emptyList();
        final List<URI> ids = _dbClient.queryByType(VirtualArray.class, true);
        nhObjList = _dbClient.queryObject(VirtualArray.class, ids);

        // filter by only authorized to use
        URI tenant = URI.create(user.getTenantId());
        for (VirtualArray nh : nhObjList) {
            if (_permissionsHelper.tenantHasUsageACL(tenant, nh)) {
                CinderAvailabiltyZone objAz = new CinderAvailabiltyZone();
                objAz.zoneName = nh.getLabel();
                objAz.zoneState.available = !nh.getInactive();
                azList.add(objAz);
            }
        }

        return azList;
    }

    /**
     * Get Volume for passed project and Id
     *      
     * @prereq none
     * 
     * @brief query and return Volume for provided Project and Id
     * @param Project, searchId
     * @return Volume
     */
    public Volume queryVolumeById(Project proj, String searchId) {
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getProjectVolumeConstraint(proj.getId()),
                uris);

        for (URI volumeUri : uris) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeUri);
            String trimmedId = trimId(volume.getId().toString());
            if (volume != null && !volume.getInactive() && (trimmedId.equalsIgnoreCase(searchId))) {
                return volume;
            }
        }
        return null;
    }
    
    /**
     * Get DataObject for passed tenant id and class type
     *      
     * @prereq none
     * 
     * @brief query and return blockObject for provided openstackId and 
     * class type like volume/snapshot/consistencygroup
     * @param openstackTenantId
     * @return Dataobject
     */
    public DataObject  queryByTag(URI openstackId, StorageOSUser user, Class<? extends DataObject> block) {
        URI vipr_tenantId = URI.create(user.getTenantId());
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(
                PrefixConstraint.Factory.getTagsPrefixConstraint(
                        block, openstackId.toString(), vipr_tenantId), uris);

        if (uris != null) {
            while (uris.iterator().hasNext()) {
                URI blockUri = uris.iterator().next();
                DataObject blockObject = _dbClient.queryObject(block, blockUri);
                if (blockObject != null) {
                    return blockObject;
                }
            }
        }
        return null;
    }

    /**
     * Get all consistency group based on tenant id
     *      
     * @prereq none
     * 
     * @brief get consistency group Uris
     * @param openstackTenantId
     * @return URIQueryResultList
     */
    protected URIQueryResultList getConsistencyGroupsUris(
            String openstackTenantId, StorageOSUser user) {
        URIQueryResultList uris = new URIQueryResultList();
        Project project = getProject(openstackTenantId,
                user);
        if (project == null) // return empty list
            return null;

        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectBlockConsistencyGroupConstraint(project.getId()),
                uris);

        return uris;
    }
   

    /**
     * Get Consistency Group Snapshot Uris
     * 
     * 
     * @prereq none
     * 
     * @param consistencyGroupsUris
     * @brief get consistency group snapshot URIS
     * @return URI list
     * 
     */
    public URIQueryResultList getConsistencyGroupSnapshotUris(URI consistencyGroupsUri) {
        URIQueryResultList snapshotUris = new URIQueryResultList();
        if (consistencyGroupsUri != null) {
            BlockConsistencyGroup blockCG = _dbClient.queryObject(
                    BlockConsistencyGroup.class, consistencyGroupsUri);
            if (null != blockCG && !(blockCG.getInactive())) {
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockSnapshotByConsistencyGroup(blockCG.getId()),
                        snapshotUris);
            }
        }
        return snapshotUris;
    }
    
    /**
     * If the Consistency Group has Snapshot(s), then Volume can not be created.
     * 
     * @param blockConsistencyGroup Block Consistency Grp Instance
     * @return 
     */
    public boolean verifyConsistencyGroupHasSnapshot(BlockConsistencyGroup consistencyGroup) {
        final URIQueryResultList cgSnapshotsResults = new URIQueryResultList();
        _dbClient.queryByConstraint(getBlockSnapshotByConsistencyGroup(consistencyGroup.getId()),
                cgSnapshotsResults);
        Iterator<BlockSnapshot> blockSnapshotIterator = _dbClient.queryIterativeObjects(BlockSnapshot.class, cgSnapshotsResults);
        if (blockSnapshotIterator.hasNext()) {
            return true;
        }
        return false;
    }

}
