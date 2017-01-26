/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.SystemsMapper.map;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.server.impl.StorageSystemTypesInitUtils;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeAddParam;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;

/**
 * StorageSystemTypes resource implementation
 */
@Path("/vdc/storage-system-types")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })

public class StorageSystemTypeService extends TaskResourceService {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypeService.class);
    private static final String EVENT_SERVICE_TYPE = "StorageSystemTypeService";
    private static final String ALL_TYPE = "all";

    /**
     * Show Storage System Type detail for given URI
     *
     * @param id
     *            the URN of Storage System Type
     * @brief Show StorageSystemType
     * @return Storage System Type details
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public StorageSystemTypeRestRep getStorageSystemType(@PathParam("id") URI id) {
        log.info("GET getStorageSystemType on Uri: " + id);

        ArgValidator.checkFieldUriType(id, StorageSystemType.class, "id");
        StorageSystemType storageType = queryResource(id);
        ArgValidator.checkEntity(storageType, id, isIdEmbeddedInURL(id));
        StorageSystemTypeRestRep storageTypeRest = new StorageSystemTypeRestRep();
        return map(storageType, storageTypeRest);
    }

    /**
     * Returns a list of all Storage System Types requested for like block,
     * file, object or all. Valid input parameters are block, file, object and
     * all
     * 
     * @brief Show list of storage system types base of type or all
     * @return List of all storage system types.
     */
    @GET
    @Path("/type/{type}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageSystemTypeList getStorageSystemTypes(@PathParam("type") String type) {
        log.info("GET getStorageSystemType on type: " + type);

        if (type != null) {
            ArgValidator.checkFieldValueFromEnum(type.toUpperCase(), "metaType",
                    StorageSystemType.META_TYPE.class);
        }

        List<URI> ids = _dbClient.queryByType(StorageSystemType.class, true);

        StorageSystemTypeList list = new StorageSystemTypeList();

        Iterator<StorageSystemType> iter = _dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (iter.hasNext()) {
            StorageSystemType ssType = iter.next();
            if (ssType.getStorageTypeId() == null) {
                ssType.setStorageTypeId(ssType.getId().toString());
            }
            if (StringUtils.equals(ALL_TYPE, type) || StringUtils.equals(type, ssType.getMetaType())) {
                list.getStorageSystemTypes().add(map(ssType));
            }
        }
        return list;
    }

    /**
     * Internal api to create a new storage system type.
     *
     * @param addparam
     *            The StorageSystemTypeAddParam object contains all the
     *            parameters for creation.
     * @brief Create storage system type. This api is available for testing.
     *
     * @return StorageSystemTypeRestRep object.
     */
    @POST
    @Path("/internal")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StorageSystemTypeRestRep addStorageSystemType(StorageSystemTypeAddParam addparam) {
        log.info("addStorageSystemType");

        ArgValidator.checkFieldNotEmpty(addparam.getStorageTypeName(), "storageTypeName");
        checkDuplicateLabel(StorageSystemType.class, addparam.getStorageTypeName());

        ArgValidator.checkFieldNotEmpty(addparam.getMetaType(), "metaType");

        ArgValidator.checkFieldNotEmpty(addparam.getDriverClassName(), "driverClassName");

        if (addparam.getIsDefaultSsl()) {
            ArgValidator.checkFieldNotEmpty(addparam.getSslPort(), "sslPort");
        } else {
            ArgValidator.checkFieldNotEmpty(addparam.getNonSslPort(), "nonSslPort");
        }

        StorageSystemType ssType = new StorageSystemType();
        URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
        ssType.setId(ssTyeUri);
        ssType.setStorageTypeId(ssTyeUri.toString());

        ssType.setStorageTypeName(addparam.getStorageTypeName());
        ssType.setMetaType(addparam.getMetaType());
        ssType.setDriverClassName(addparam.getDriverClassName());

        if (addparam.getStorageTypeDispName() != null) {
            ssType.setStorageTypeDispName(addparam.getStorageTypeDispName());
        }

        if (addparam.getNonSslPort() != null) {
            ssType.setNonSslPort(addparam.getNonSslPort());
        }

        if (addparam.getSslPort() != null) {
            ssType.setSslPort(addparam.getSslPort());
        }

        ssType.setIsSmiProvider(addparam.getIsSmiProvider());
        ssType.setIsDefaultSsl(addparam.getIsDefaultSsl());
        ssType.setIsDefaultMDM(addparam.getIsDefaultMDM());
        ssType.setIsOnlyMDM(addparam.getIsOnlyMDM());
        ssType.setIsElementMgr(addparam.getIsElementMgr());
        ssType.setIsSecretKey(addparam.getIsSecretKey());
        ssType.setIsNative(addparam.getIsNative());

        _dbClient.createObject(ssType);

        auditOp(OperationTypeEnum.ADD_STORAGE_SYSTEM_TYPE, true, AuditLogManager.AUDITOP_BEGIN,
                ssType.getId().toString(), ssType.getStorageTypeName(), ssType.getMetaType());
        return map(ssType);
    }

    /**
     * Internal api to delete existing Storage System Type.
     *
     * @param id storage system type id
     *
     * @brief Delete Storage System Type.
     * @return No data returned in response body
     */
    @POST
    @Path("/internal/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteStorageSystemType(@PathParam("id") URI id) {
        log.info("deleteStorageSystemType: {}", id);
        // Name of Array and its Display Name mapping, cannot delete native
        // drivers
        Map<String, String> nativeDriverNameMap = StorageSystemTypesInitUtils.getDisplayNames();

        StorageSystemType sstype = queryObject(StorageSystemType.class, id, true);
        ArgValidator.checkEntity(sstype, id, isIdEmbeddedInURL(id));
        if (nativeDriverNameMap.get(sstype.getStorageTypeName()) == null) {
            _dbClient.markForDeletion(sstype);

            auditOp(OperationTypeEnum.REMOVE_STORAGE_SYSTEM_TYPE, true, AuditLogManager.AUDITOP_BEGIN,
                    sstype.getId().toString(), sstype.getStorageTypeName(), sstype.getMetaType());
            return Response.ok().build();
        } else {
            return Response.status(403).build();
        }

    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected StorageSystemType queryResource(URI id) {
        ArgValidator.checkUri(id);
        StorageSystemType storageType = _dbClient.queryObject(StorageSystemType.class, id);
        ArgValidator.checkEntity(storageType, id, isIdEmbeddedInURL(id));
        return storageType;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.STORAGE_SYSTEM_TYPE;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

}
