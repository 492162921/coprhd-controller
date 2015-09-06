/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BucketMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapBucket;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.BucketRecommendation;
import com.emc.storageos.api.service.impl.placement.BucketScheduler;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.object.BucketBulkRep;
import com.emc.storageos.model.object.BucketDeleteParam;
import com.emc.storageos.model.object.BucketParam;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ObjectController;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

@Path("/object/buckets")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
public class BucketService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(BucketService.class);
    private static final String EVENT_SERVICE_TYPE = "object";

    private BucketScheduler _bucketScheduler;
    private NameGenerator _nameGenerator;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Bucket> getResourceClass() {
        return Bucket.class;
    }

    @Override
    public BucketBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<Bucket> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new BucketBulkRep(BulkList.wrapping(_dbIterator, MapBucket.getInstance()));
    }

    @Override
    protected BucketBulkRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        Iterator<Bucket> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter<Bucket> filter = new BulkList.ProjectResourceFilter<Bucket>(
                getUserFromContext(), _permissionsHelper);
        return new BucketBulkRep(BulkList.wrapping(_dbIterator, MapBucket.getInstance(), filter));
    }

    public NameGenerator getNameGenerator() {
        return _nameGenerator;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    public void setBucketScheduler(BucketScheduler bucketScheduler) {
        _bucketScheduler = bucketScheduler;
    }

    /**
     * Creates bucket.
     * 
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param param Bucket parameters
     * @param id the URN of a ViPR Project
     * @brief Create Bucket
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep createBucket(BucketParam param, @QueryParam("project") URI id) throws InternalException {
        // check project
        ArgValidator.checkFieldUriType(id, Project.class, "project");

        // Make label as mandatory field
        ArgValidator.checkFieldNotNull(param.getLabel(), "label");

        Project project = _permissionsHelper.getObjectById(id, Project.class);
        ArgValidator.checkEntity(project, id, isIdEmbeddedInURL(id));
        ArgValidator.checkFieldNotNull(project.getTenantOrg(), "project");
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
        return initiateBucketCreation(param, project, tenant, null);
    }

    private TaskResourceRep initiateBucketCreation(BucketParam param, Project project,
            TenantOrg tenant, DataObject.Flag[] flags) throws InternalException {
        ArgValidator.checkFieldUriType(param.getVpool(), VirtualPool.class, "vpool");
        ArgValidator.checkFieldUriType(param.getVarray(), VirtualArray.class, "varray");

        Long softQuota = SizeUtil.translateSize(param.getSoftQuota());
        Long hardQuota = SizeUtil.translateSize(param.getHardQuota());
        Integer retention = Integer.getInteger(param.getRetention(), 0);

        // Hard Quota should be more than SoftQuota
        if (softQuota >= hardQuota) {
            throw APIException.badRequests.invalidQuotaRequestForObjectStorage(param.getLabel());
        }

        // check varray
        VirtualArray neighborhood = _dbClient.queryObject(VirtualArray.class, param.getVarray());
        ArgValidator.checkEntity(neighborhood, param.getVarray(), false);
        _permissionsHelper.checkTenantHasAccessToVirtualArray(tenant.getId(), neighborhood);

        // check vpool reference
        VirtualPool cos = _dbClient.queryObject(VirtualPool.class, param.getVpool());
        _permissionsHelper.checkTenantHasAccessToVirtualPool(tenant.getId(), cos);
        ArgValidator.checkEntity(cos, param.getVpool(), false);
        if (!VirtualPool.Type.object.name().equals(cos.getType())) {
            throw APIException.badRequests.virtualPoolNotForObjectStorage(VirtualPool.Type.object.name());
        }

        // verify quota
        CapacityUtils.validateQuotasForProvisioning(_dbClient, cos, project, tenant, hardQuota, "bucket");

        // verify retention
        if (retention > cos.getMaxRetention()) {
            throw APIException.badRequests.insufficientRetentionForVirtualPool(cos.getLabel(), "bucket");
        }

        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, hardQuota);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.FALSE);

        List<BucketRecommendation> placement = _bucketScheduler.placeBucket(neighborhood, cos, capabilities);
        if (placement.isEmpty()) {
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(cos.getId(), neighborhood.getId());
        }

        // Randomly select a recommended pool
        Collections.shuffle(placement);
        BucketRecommendation recommendation = placement.get(0);

        // Check if there already exist a bucket with same name.
        checkForDuplicateName(param.getLabel(), Bucket.class, null, null, _dbClient);

        String task = UUID.randomUUID().toString();
        Bucket bucket = prepareBucket(param, project, tenant, neighborhood, cos, flags, recommendation, task);

        _log.info(String.format(
                "createBucket --- Bucket: %1$s, StoragePool: %2$s, StorageSystem: %3$s",
                bucket.getId(), recommendation.getSourcePool(), recommendation.getSourceDevice()));

        // TODO : Controller call
        try {
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, recommendation.getSourceDevice());
            ObjectController controller = getController(ObjectController.class, system.getSystemType());
            controller.createBucket(recommendation.getSourceDevice(), param.getLabel());
            _dbClient.persistObject(bucket);
            Operation tempOperation = new Operation();
            tempOperation.ready();
            bucket.getOpStatus().updateTaskStatus(task, tempOperation);
        } catch (InternalException e) {
            bucket.setInactive(true);
            _dbClient.persistObject(bucket);

            // treating all controller exceptions as internal error for now. controller
            // should discriminate between validation problems vs. internal errors
            throw e;
        }

        auditOp(OperationTypeEnum.CREATE_BUCKET, true, AuditLogManager.AUDITOP_BEGIN,
                param.getLabel(), param.getHardQuota(), neighborhood.getId().toString(),
                project == null ? null : project.getId().toString());

        return toTask(bucket, task);
    }

    /**
     * Get info for Bucket
     * 
     * @param id the URN of a ViPR Bucket
     * @return Bucket details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BucketRestRep getBucket(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Bucket.class, "id");
        Bucket fs = queryResource(id);
        return map(fs);
    }

    @Override
    protected Bucket queryResource(URI id) {
        ArgValidator.checkUri(id);
        Bucket bucket = _permissionsHelper.getObjectById(id, Bucket.class);
        ArgValidator.checkEntityNotNull(bucket, id, isIdEmbeddedInURL(id));
        return bucket;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        Bucket bucket = queryResource(id);
        return bucket.getTenant().getURI();
    }

    /**
     * Deactivate Bucket, this will move the Bucket to a "marked-for-delete" state
     * it will be deleted when all references to this Bucket of type Snapshot are deleted.
     * 
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id the URN of a ViPR Bucket
     * @param param Bucket delete param for optional force delete
     * @brief Delete Bucket
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep deactivateBucket(@PathParam("id") URI id, BucketDeleteParam param) throws InternalException {

        String task = UUID.randomUUID().toString();
        _log.info(String.format(
                "BucketDelete --- Bucket id: %1$s, Task: %2$s, ForceDelete: %3$s", id, task, param.getForceDelete()));
        ArgValidator.checkFieldUriType(id, Bucket.class, "id");
        Bucket bucket = queryResource(id);
        if (!param.getForceDelete()) {
            ArgValidator.checkReference(Bucket.class, id, checkForDelete(bucket));
        }
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, bucket.getStorageDevice());

        Operation op = _dbClient.createTaskOpStatus(Bucket.class, bucket.getId(),
                task, ResourceOperationTypeEnum.DELETE_BUCKET);
        op.setDescription("Bucket deactivate");
        // TODO : Implement controller operation
        /*
         * BucketController controller = getController(Bucket.class, device.getSystemType());
         * controller.delete(device.getId(), null, bucket.getId(), param.getForceDelete(), task);
         */
        //TODO : Bucket delete needs to happen in Controller. Remove this code.
        bucket.setInactive(Boolean.TRUE);
        _dbClient.persistObject(bucket);
        
        auditOp(OperationTypeEnum.DELETE_BUCKET, true, AuditLogManager.AUDITOP_BEGIN,
                bucket.getId().toString(), device.getId().toString());

        return toTask(bucket, task, op);
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of Bucket resources
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public BucketBulkRep getBulkResources(BulkIdParam param) {
        return (BucketBulkRep) super.getBulkResources(param);
    }

    /**
     * Allocate, initialize and persist state of the Bucket being created.
     * 
     * @param param
     * @param project
     * @param tenantOrg
     * @param neighborhood
     * @param vpool
     * @param flags
     * @param placement
     * @param token
     * @return
     */
    private Bucket prepareBucket(BucketParam param, Project project, TenantOrg tenantOrg,
            VirtualArray neighborhood, VirtualPool vpool, DataObject.Flag[] flags, BucketRecommendation placement, String token) {
        _log.debug("Preparing Bucket creation for Param : {}", param);
        StoragePool pool = null;
        Bucket bucket = new Bucket();
        bucket.setId(URIUtil.createId(Bucket.class));

        bucket.setLabel(param.getLabel());

        // No need to generate any name -- Since the requirement is to use the customizing label we should use the same.
        // Stripping out the special characters like ; /-+!@#$%^&())";:[]{}\ | but allow underscore character _
        String convertedName = param.getLabel().replaceAll("[^\\dA-Za-z\\_]", "");
        _log.info("Original name {} and converted name {}", param.getLabel(), convertedName);
        bucket.setLabel(convertedName);
        bucket.setHardQuota(SizeUtil.translateSize(param.getHardQuota()));
        bucket.setSoftQuota(SizeUtil.translateSize(param.getSoftQuota()));
        bucket.setVirtualPool(param.getVpool());
        if (project != null) {
            bucket.setProject(new NamedURI(project.getId(), bucket.getLabel()));
        }
        bucket.setTenant(new NamedURI(tenantOrg.getId(), param.getLabel()));
        bucket.setVirtualArray(neighborhood.getId());

        if (null != placement.getSourcePool()) {
            pool = _dbClient.queryObject(StoragePool.class, placement.getSourcePool());
            if (null != pool) {
                bucket.setProtocol(new StringSet());
                bucket.getProtocol().addAll(VirtualPoolUtil.getMatchingProtocols(vpool.getProtocols(), pool.getProtocols()));
            }
        }

        bucket.setStorageDevice(placement.getSourceDevice());
        bucket.setPool(placement.getSourcePool());
        bucket.setOpStatus(new OpStatusMap());

        if (flags != null) {
            bucket.addInternalFlags(flags);
        }
        _dbClient.createObject(bucket);
        return bucket;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.BUCKET;
    }

    /**
     * Bucket is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    /**
     * Get search results by project alone.
     * 
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getProjectSearchResults(URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getProjectBucketConstraint(projectId),
                resRepList);
        return resRepList;
    }

    /**
     * Get object specific permissions filter
     * 
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        return new ProjOwnedResRepFilter(user, permissionsHelper, Bucket.class);
    }
}
