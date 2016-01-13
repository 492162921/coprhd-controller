/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyUtils;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.VolumeGroup.VolumeGroupRole;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupCreateParam;
import com.emc.storageos.model.application.VolumeGroupList;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.block.NamedVolumeGroupsList;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.storageos.model.block.VolumeGroupFullCopyDetachParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * APIs to view, create, modify and remove volume groups
 */

@Path("/volume-groups/block")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class VolumeGroupService extends TaskResourceService {
    private static final String VOLUME_GROUP_NAME = "name";
    private static final String VOLUME_GROUP_ROLES = "roles";
    private static final String EVENT_SERVICE_TYPE = "application";
    private static final Set<String> ALLOWED_SYSTEM_TYPES = new HashSet<String>(Arrays.asList(
            DiscoveredDataObject.Type.vnxblock.name(),
            DiscoveredDataObject.Type.vplex.name(),
            DiscoveredDataObject.Type.vmax.name(),
            DiscoveredDataObject.Type.xtremio.name(),
            DiscoveredDataObject.Type.scaleio.name(),
            DiscoveredDataObject.Type.rp.name(),
            DiscoveredDataObject.Type.srdf.name(),
            DiscoveredDataObject.Type.ibmxiv.name()));
    private static final Set<String> BLOCK_TYPES = new HashSet<String>(Arrays.asList(
            DiscoveredDataObject.Type.vnxblock.name(),
            DiscoveredDataObject.Type.vmax.name(),
            DiscoveredDataObject.Type.xtremio.name(),
            DiscoveredDataObject.Type.scaleio.name(),
            DiscoveredDataObject.Type.ibmxiv.name(),
            DiscoveredDataObject.Type.srdf.name()));
    private static final String BLOCK = "block";

    static final Logger log = LoggerFactory.getLogger(VolumeGroupService.class);

    // A reference to the placement manager.
    private PlacementManager _placementManager;

    // Block service implementations
    private static Map<String, BlockServiceApi> _blockServiceApis;

    /**
     * Setter for the placement manager.
     *
     * @param placementManager A reference to the placement manager.
     */
    public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

    public void setBlockServiceApis(final Map<String, BlockServiceApi> serviceInterfaces) {
        _blockServiceApis = serviceInterfaces;
    }

    private static BlockServiceApi getBlockServiceImpl(final String type) {
        return _blockServiceApis.get(type);
    }


    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        VolumeGroup volumeGroup = _permissionsHelper.getObjectById(id, VolumeGroup.class);
        ArgValidator.checkEntityNotNull(volumeGroup, id, isIdEmbeddedInURL(id));
        return volumeGroup;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VOLUME_GROUP;
    }

    @Override
    protected URI getTenantOwner(final URI id) {
        return null;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Create a volume group
     *
     * @param param Parameters for creating a volume group
     * @return created volume group
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VolumeGroupRestRep createVolumeGroup(VolumeGroupCreateParam param) {
        ArgValidator.checkFieldNotEmpty(param.getName(), VOLUME_GROUP_NAME);
        checkDuplicateLabel(VolumeGroup.class, param.getName());
        Set<String> roles = param.getRoles();
        ArgValidator.checkFieldNotEmpty(roles, VOLUME_GROUP_ROLES);
        for (String role : roles) {
            ArgValidator.checkFieldValueFromEnum(role, VOLUME_GROUP_ROLES, VolumeGroup.VolumeGroupRole.class);
        }
        VolumeGroup volumeGroup = new VolumeGroup();
        volumeGroup.setId(URIUtil.createId(VolumeGroup.class));
        volumeGroup.setLabel(param.getName());
        volumeGroup.setDescription(param.getDescription());
        volumeGroup.addRoles(param.getRoles());

        // add parent if specified
        String msg = setParent(volumeGroup, param.getParent());
        if (msg != null && !msg.isEmpty()) {
            throw APIException.badRequests.volumeGroupCantBeCreated(volumeGroup.getLabel(), msg);
        }

        _dbClient.createObject(volumeGroup);
        auditOp(OperationTypeEnum.CREATE_VOLUME_GROUP, true, null, volumeGroup.getId().toString(),
                volumeGroup.getLabel());
        return DbObjectMapper.map(volumeGroup);
    }

    /**
     * List a volume group
     *
     * @param id volume group Id
     * @return ApplicationRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public VolumeGroupRestRep getVolumeGroup(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = (VolumeGroup) queryResource(id);
        VolumeGroupRestRep resp = DbObjectMapper.map(volumeGroup);
        resp.setReplicationGroupNames(CopyVolumeGroupUtils.getReplicationGroupNames(volumeGroup, _dbClient));
        return resp;
    }

    /**
     * List volume groups.
     *
     * @return A reference to VolumeGroupList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VolumeGroupList getVolumeGroups() {
        VolumeGroupList volumeGroupList = new VolumeGroupList();

        List<URI> ids = _dbClient.queryByType(VolumeGroup.class, true);
        Iterator<VolumeGroup> iter = _dbClient.queryIterativeObjects(VolumeGroup.class, ids);
        while (iter.hasNext()) {
            volumeGroupList.getVolumeGroups().add(toNamedRelatedResource(iter.next()));
        }
        return volumeGroupList;
    }

    /**
     * Get application volumes
     *
     * @param id Application Id
     * @return NamedVolumesList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/volumes")
    public NamedVolumesList getVolumes(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = (VolumeGroup) queryResource(id);
        NamedVolumesList result = new NamedVolumesList();
        List<Volume> volumes = getVolumeGroupVolumes(_dbClient, volumeGroup);
        for (Volume volume: volumes) {
            result.getVolumes().add(toNamedRelatedResource(volume));
        }
        return result;
    }

    /**
     * Get the list of child volume groups
     * 
     * @param id
     * @return
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/volume-groups")
    public NamedVolumeGroupsList getChildrenVolumeGroups(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = _dbClient.queryObject(VolumeGroup.class, id);
        NamedVolumeGroupsList result = new NamedVolumeGroupsList();
        List<VolumeGroup> volumeGroups = getVolumeGroupChildren(_dbClient, volumeGroup);
        for (VolumeGroup group : volumeGroups) {
            result.getVolumeGroups().add(toNamedRelatedResource(group));
        }
        return result;
    }

    /**
     * Delete the volume group.
     * When a volume group is deleted it will move to a "marked for deletion" state.
     *
     * @param id the URN of the volume group
     * @brief Deactivate application
     * @return No data returned in response body
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public Response deactivateVolumeGroup(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = (VolumeGroup) queryResource(id);

        if (!getVolumeGroupVolumes(_dbClient, volumeGroup).isEmpty()) {
            // application could not be deleted if it has volumes
            throw APIException.badRequests.volumeGroupWithVolumesCantBeDeleted(volumeGroup.getLabel());
        }

        if (!getVolumeGroupChildren(_dbClient, volumeGroup).isEmpty()) {
            // application could not be deleted if it has child volume groups
            throw APIException.badRequests.volumeGroupWithChildrenCantBeDeleted(volumeGroup.getLabel());
        }

        // check for any other references to this volume group
        ArgValidator.checkReference(VolumeGroup.class, id, checkForDelete(volumeGroup));

        _dbClient.markForDeletion(volumeGroup);

        auditOp(OperationTypeEnum.DELETE_CONFIG, true, null, id.toString(),
                volumeGroup.getLabel());
        return Response.ok().build();
    }

    /**
     * update a volume group
     * @param id volume group id
     * @param param volume group update parameters
     * @return
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList updateVolumeGroup(@PathParam("id") final URI id,
            final VolumeGroupUpdateParam param) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = (VolumeGroup) queryResource(id);
        if (volumeGroup.getInactive()) {
            throw APIException.badRequests.volumeGroupCantBeUpdated(volumeGroup.getLabel(), "The Volume Group has been deleted");
        }
        checkForApplicationPendingTasks(volumeGroup);
        boolean isChanged = false;
        String vgName = param.getName();
        if (vgName != null && !vgName.isEmpty() && !vgName.equalsIgnoreCase(volumeGroup.getLabel())) {
            checkDuplicateLabel(VolumeGroup.class, vgName);
            volumeGroup.setLabel(vgName);
            isChanged = true;
        }
        String description = param.getDescription();
        if (description != null && !description.isEmpty()) {
            volumeGroup.setDescription(description);
            isChanged = true;
        }

        String parent = param.getParent();
        if (parent != null && !parent.isEmpty()) {
            String msg = setParent(volumeGroup, parent);
            if (msg != null && !msg.isEmpty()) {
                throw APIException.badRequests.volumeGroupCantBeUpdated(volumeGroup.getLabel(), msg);
            }
            isChanged = true;
        }

        if (isChanged) {
            _dbClient.updateObject(volumeGroup);
        }
        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = null;
        if (!param.hasEitherAddOrRemoveVolumes()) {
            op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
            op.ready();
            volumeGroup.getOpStatus().createTaskStatus(taskId, op);
            _dbClient.updateObject(volumeGroup);
            TaskResourceRep task = toTask(volumeGroup, taskId, op);
            taskList.getTaskList().add(task);
            return taskList;
        }

        List<VolumeGroupUtils> utils = getVolumeGroupUtils(volumeGroup);
        for (VolumeGroupUtils util : utils) {
            util.validateUpdateVolumesInVolumeGroup(_dbClient, param, volumeGroup);
        }
        for (VolumeGroupUtils util : utils) {
            util.updateVolumesInVolumeGroup(_dbClient, param, volumeGroup, taskId, taskList);
        }
        auditOp(OperationTypeEnum.UPDATE_VOLUME_GROUP, true, AuditLogManager.AUDITOP_BEGIN, volumeGroup.getId().toString(),
                volumeGroup.getLabel());
        return taskList;
    }

    /**
     * Creates a volume group fullcopy
     * TODO update doc
     *
     * @prereq none
     *
     * @param volumeGroupId the URI of the Volume Group
     *            - Volume group URI
     * @param param VolumeFullCopyCreateParam
     *
     * @brief Create volume group fullcopy
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN }, acls = { ACL.ANY })
    public TaskList createVolumeGroupFullCopy(@PathParam("id") final URI volumeGroupId,
            final VolumeFullCopyCreateParam param) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, "full copy");

        TaskList taskList = new TaskList();

        // get all volumes
        List<Volume> volumes = BlockServiceUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);
        // validate that there should be some volumes in VolumeGroup
        if (volumes.isEmpty()) {
            throw APIException.badRequests.replicaOperationNotAllowedOnEmptyVolumeGroup(volumeGroup.getLabel(), "Full copy");
        }

        List<VolumeGroupUtils> utils = getVolumeGroupUtils(volumeGroup);
        for (VolumeGroupUtils util : utils) {
            // TODO XtremIO array does not support clone.
            // If volume group has mix of storage arrays, entire Clone creation workflow will fail (rolled back)
            // In such cases and not to have partial clone, we may need to restrict user at API level.
            // may be use Copy-VolumeGroupUtils to validate such things.
        }

        if (param.getPartial()) {
            log.info("Full Copy requested for subset of array groups in Application.");

            // validate that at least one volume URI is provided
            ArgValidator.checkFieldNotEmpty(param.getVolumes(), "volumes");

            // validate that provided volumes
            List<String> arrayGroupNames = new ArrayList<String>();
            List<Volume> volumesInRequest = new ArrayList<Volume>();
            for (URI volumeURI : param.getVolumes()) {
                ArgValidator.checkFieldUriType(volumeURI, Volume.class, "volume");
                // Get the Volume.
                Volume volume = (Volume) BlockFullCopyUtils.queryFullCopyResource(volumeURI,
                        uriInfo, true, _dbClient);
                ArgValidator.checkEntityNotNull(volume, volumeURI, isIdEmbeddedInURL(volumeURI));

                // skip repeated array groups
                if (arrayGroupNames.contains(volume.getReplicationGroupInstance())) {
                    log.info("Skipping repetitive request for Volume array group {}. Volume: {}",
                            volume.getReplicationGroupInstance(), volume.getLabel());
                    continue;
                }
                arrayGroupNames.add(volume.getReplicationGroupInstance());

                // validate that provided volumes are part of Volume Group
                if (!volumeGroupId.equals(volume.getApplication(_dbClient).getId())) {
                    throw APIException.badRequests
                            .replicaOperationNotAllowedVolumeNotInVolumeGroup("Full copy", volume.getLabel());
                }

                volumesInRequest.add(volume);
            }

            // send create request after validating all volumes
            for (Volume volume : volumesInRequest) {
                // set Flag in Volume so that we will know about partial request during processing.
                volume.addInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST);
                _dbClient.updateObject(volume);

                // Create full copy. Note that it will take into account the
                // fact that the volume is in a ReplicationGroup
                // and full copies will be created for all volumes in that ReplicationGroup.

                // In case of partial request, Tasks will be generated for each Array group
                // and they cannot be monitored together.
                taskList.getTaskList().addAll(getFullCopyManager().createFullCopy(volume.getId(), param).getTaskList());
            }
        } else {
            log.info("Full Copy requested for entire Application");
            auditOp(OperationTypeEnum.CREATE_VOLUME_GROUP_FULL_COPY, true, AuditLogManager.AUDITOP_BEGIN, volumeGroup.getId().toString(),
                    param.getName(), param.getCount());

            // Full copy will be created for all volumes in Application
            taskList = getFullCopyManager().createFullCopy(volumes.get(0).getId(), param);
        }

        return taskList;
    }

    /**
     * List full copies for a volume group
     *
     * @prereq none
     *
     * @param cgURI The URI of the volume group.
     *
     * @brief List full copies for a volume group
     *
     * @return The list of full copies for the volume group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public NamedVolumesList getVolumeGroupFullCopies(@PathParam("id") final URI volumeGroupId) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, "full copy");

        // get all volumes
        List<Volume> volumes = BlockServiceUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);

        // Cycle over the volumes in the volume group and
        // get the full copies for each volume in the group.
        NamedVolumesList fullCopyList = new NamedVolumesList();
        for (Volume volume : volumes) {
            NamedVolumesList volumeFullCopies = getFullCopyManager().getFullCopiesForSource(volume.getId());
            fullCopyList.getVolumes().addAll(volumeFullCopies.getVolumes());
        }

        return fullCopyList;
    }

    /**
     * Get the specified volume group full copy.
     *
     * @prereq none
     *
     * @param cgURI The URI of the volume group.
     * @param fullCopyURI The URI of the full copy.
     *
     * @brief Get the specified volume group full copy.
     *
     * @return The full copy volume.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/{fcid}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public VolumeRestRep getVolumeGroupFullCopy(@PathParam("id") final URI volumeGroupId,
            @PathParam("fcid") URI fullCopyURI) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, "full copy");

        // get all volumes
        List<Volume> volumes = BlockServiceUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);

        Volume fullCopyVolume = (Volume) BlockFullCopyUtils.queryFullCopyResource(
                fullCopyURI, uriInfo, false, _dbClient);
        verifyReplicaForCopyRequest(fullCopyVolume, volumes);

        // Get and return the full copy.
        return getFullCopyManager().getFullCopy(fullCopyURI);
    }

    /**
     * Detach the specified Volume group full copy.
     * TODO update doc
     *
     * @prereq Create Volume group full copy as active.
     *
     * @param volumeGroupId The URI of the Volume group.
     * @param fullCopyURI The URI of the full copy.
     *
     * @brief Detach Volume group full copy.
     *
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/detach")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList detachVolumeGroupFullCopy(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupFullCopyDetachParam param) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);
        TaskList taskList = new TaskList();

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, "full copy");

        // validate that at least one full copy URI is provided
        ArgValidator.checkFieldNotEmpty(param.getFullCopies(), "volumes");

        // get all volumes
        List<Volume> volumes = BlockServiceUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);

        List<VolumeGroupUtils> utils = getVolumeGroupUtils(volumeGroup);
        for (VolumeGroupUtils util : utils) {
            //
        }

        List<String> arrayGroupNames = new ArrayList<String>();
        List<Volume> fullCopyVolumesInRequest = new ArrayList<Volume>();
        for (URI fullCopyURI : param.getFullCopies()) {
            ArgValidator.checkFieldUriType(fullCopyURI, Volume.class, "volume");
            // Get the full copy.
            Volume fullCopyVolume = (Volume) BlockFullCopyUtils.queryFullCopyResource(
                    fullCopyURI, uriInfo, false, _dbClient);
            ArgValidator.checkEntityNotNull(fullCopyVolume, fullCopyURI, isIdEmbeddedInURL(fullCopyURI));

            // skip repeated array groups
            if (arrayGroupNames.contains(fullCopyVolume.getReplicationGroupInstance())) {
                log.info("Skipping repetitive request for Full Copy array group {}. Full Copy: {}",
                        fullCopyVolume.getReplicationGroupInstance(), fullCopyVolume.getLabel());
                continue;
            }
            arrayGroupNames.add(fullCopyVolume.getReplicationGroupInstance());

            URI fcSourceURI = fullCopyVolume.getAssociatedSourceVolume();
            if (!NullColumnValueGetter.isNullURI(fcSourceURI)) {
                verifyReplicaForCopyRequest(fullCopyVolume, volumes);
            }

            fullCopyVolumesInRequest.add(fullCopyVolume);
        }

        for (Volume fullCopyVolume : fullCopyVolumesInRequest) {
            URI fcSourceURI = fullCopyVolume.getAssociatedSourceVolume();
            if (param.getPartial()) {
                log.info("Full Copy operation requested for subset of array groups in Application. Processing Full copy {}",
                        fullCopyVolume.getLabel());
                // set Flag in Clone so that we will know about partial request during processing.
                fullCopyVolume.addInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST);
                _dbClient.updateObject(fullCopyVolume);

                // Detach the full copy. Note that it will take into account the
                // fact that the volume is in a ReplicationGroup
                // and all volumes in that ReplicationGroup will be detached.

                // In case of partial request, Tasks will be generated for each Array group
                // and they cannot be monitored together.
                taskList.getTaskList().addAll(getFullCopyManager().detachFullCopy(fcSourceURI, fullCopyVolume.getId()).getTaskList());
            } else {
                log.info("Full Copy operation requested for entire Application");
                auditOp(OperationTypeEnum.DETACH_VOLUME_GROUP_FULL_COPY, true, AuditLogManager.AUDITOP_BEGIN,
                        volumeGroup.getId().toString(), fullCopyVolume.getLabel());

                // Detach the full copy. Note that it will take into account the
                // fact that the volume is in a VolumeGroup
                // and all volumes in that VolumeGroup will be detached.
                taskList = getFullCopyManager().detachFullCopy(fcSourceURI, fullCopyVolume.getId());
                break;  // for full request, we need to process only once.
            }
        }

        return taskList;
    }

    /**
     * allow replica operation only for COPY type VolumeGroup.
     * 
     * @param volumeGroup
     * @param replicaType
     */
    private void validateCopyOperationForVolumeGroup(VolumeGroup volumeGroup, String replicaType) {
        if (!volumeGroup.getRoles().contains(VolumeGroupRole.COPY.name())) {
            throw APIException.badRequests.replicaOperationNotAllowedForNonCopyTypeVolumeGroup(volumeGroup.getLabel(), replicaType);
        }
    }


    private List<VolumeGroupUtils> getVolumeGroupUtils(VolumeGroup volumeGroup) {
        List<VolumeGroupUtils> utilsList = new ArrayList<VolumeGroupUtils>();

        if (volumeGroup.getRoles().contains(VolumeGroup.VolumeGroupRole.COPY.toString())) {
            utilsList.add(new CopyVolumeGroupUtils());
        }
        if (volumeGroup.getRoles().contains(VolumeGroup.VolumeGroupRole.MOBILITY.toString())) {
            utilsList.add(new MobilityVolumeGroupUtils());
        }
        if (volumeGroup.getRoles().contains(VolumeGroup.VolumeGroupRole.DR.toString())) {
            utilsList.add(new DRVolumeGroupUtils());
        }

        return utilsList;
    }

    private static abstract class VolumeGroupUtils {
        /**
         * @param param
         * @param volumeGroup
         * @param taskId
         * @param taskList
         * @return
         */
        public abstract void updateVolumesInVolumeGroup(DbClient dbClient, final VolumeGroupUpdateParam param, VolumeGroup volumeGroup, String taskId, TaskList taskList);

        /**
         * @param dbClient
         * @param param
         * @param volumeGroup
         * @param taskId
         * @param taskList
         */
        public abstract void validateUpdateVolumesInVolumeGroup(DbClient dbClient, final VolumeGroupUpdateParam param, VolumeGroup volumeGroup);

        protected void updateVolumeObjects(DbClient dbClient, List<Volume> addVols, List<Volume> removeVols, VolumeGroup volumeGroup) {
            for (Volume addVol : addVols) {
                addVol.getVolumeGroupIds().add(volumeGroup.getId().toString());
            }
            for (Volume remVol : removeVols) {
                remVol.getVolumeGroupIds().remove(volumeGroup.getId().toString());
            }
            dbClient.updateObject(addVols);
            dbClient.updateObject(removeVols);
        }

        /**
         * Add task for volumes and consistency groups
         *
         * @param addVols
         * @param removeVols
         * @param removeVolumeCGs
         * @param taskId
         * @param taskList
         */
        protected void addTasksForVolumesAndCGs(DbClient dbClient, List<Volume> addVols, List<Volume> removeVols, Set<URI> removeVolumeCGs,
                String taskId, TaskList taskList) {
            if (addVols != null && !addVols.isEmpty()) {
                for (Volume vol : addVols) {
                    addVolumeTask(dbClient, vol, taskList, taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
                }
            }
            if (removeVols != null && !removeVols.isEmpty()) {
                for (Volume vol : removeVols) {
                    addVolumeTask(dbClient, vol, taskList, taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
                }
            }

            if (removeVolumeCGs != null && !removeVolumeCGs.isEmpty()) {
                for (URI cg : removeVolumeCGs) {
                    addConsistencyGroupTask(dbClient, cg, taskList, taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
                }
            }
        }

        /**
         * @param dbClient
         * @param uriList
         * @param taskId
         * @param e
         */
        protected void updateFailedVolumeTasks(DbClient dbClient, List<URI> uriList, String taskId, ServiceCoded e) {
            for (URI uri : uriList) {
                Volume vol = dbClient.queryObject(Volume.class, uri);
                Operation op = vol.getOpStatus().get(taskId);
                if (op != null) {
                    op.error(e);
                    vol.getOpStatus().updateTaskStatus(taskId, op);
                    dbClient.updateObject(vol);
                }
            }
        }

        /**
         * Creates tasks against consistency group associated with a request and adds them to the given task list.
         *
         * @param group
         * @param taskList
         * @param taskId
         * @param operationTypeEnum
         */
        private void addConsistencyGroupTask(DbClient dbClient, URI groupUri, TaskList taskList,
                String taskId,
                ResourceOperationTypeEnum operationTypeEnum) {
            BlockConsistencyGroup group = dbClient.queryObject(BlockConsistencyGroup.class, groupUri);
            Operation op = dbClient.createTaskOpStatus(BlockConsistencyGroup.class, group.getId(), taskId,
                    operationTypeEnum);
            taskList.getTaskList().add(TaskMapper.toTask(group, taskId, op));
        }

        /**
         * Creates tasks against volume associated with a request and adds them to the given task list.
         *
         * @param volume
         * @param taskList
         * @param taskId
         * @param operationTypeEnum
         */
        private void addVolumeTask(DbClient dbClient, Volume volume, TaskList taskList,
                String taskId,
                ResourceOperationTypeEnum operationTypeEnum) {
            Operation op = dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId,
                    operationTypeEnum);
            taskList.getTaskList().add(TaskMapper.toTask(volume, taskId, op));
        }
    }

    private static class MobilityVolumeGroupUtils extends VolumeGroupUtils {

        /* (non-Javadoc)
         * @see com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#updateVolumesInVolumeGroup(com.emc.storageos.db.client.DbClient, java.net.URI, com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup, java.lang.String, com.emc.storageos.model.TaskList)
         */
        @Override
        public void updateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup, String taskId,
                TaskList taskList) {

            List<Volume> removeVols = new ArrayList<Volume>();
            List<Volume> addVols = new ArrayList<Volume>();

            if (param.hasVolumesToAdd()) {
                Iterator<Volume> addVolItr = dbClient.queryIterativeObjects(Volume.class, param.getAddVolumesList().getVolumes());
                while (addVolItr.hasNext()) {
                    addVols.add(addVolItr.next());
                }
            }
            if (param.hasVolumesToRemove()) {
                Iterator<Volume> remVolItr = dbClient.queryIterativeObjects(Volume.class, param.getRemoveVolumesList().getVolumes());
                while (remVolItr.hasNext()) {
                    removeVols.add(remVolItr.next());
                }
            }

            Operation op = dbClient.createTaskOpStatus(VolumeGroup.class, volumeGroup.getId(),
                    taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
            taskList.getTaskList().add(toTask(volumeGroup, taskId, op));
            addTasksForVolumesAndCGs(dbClient, addVols, removeVols, null, taskId, taskList);

            try {
                updateVolumeObjects(dbClient, addVols, removeVols, volumeGroup);
            }  catch (InternalException | APIException e) {
                VolumeGroup app = dbClient.queryObject(VolumeGroup.class, volumeGroup.getId());
                op = app.getOpStatus().get(taskId);
                op.error(e);
                app.getOpStatus().updateTaskStatus(taskId, op);
                dbClient.updateObject(app);
                if (param.hasVolumesToAdd()) {
                    List<URI> addURIs = param.getAddVolumesList().getVolumes();
                    updateFailedVolumeTasks(dbClient, addURIs, taskId, e);
                }
                if (param.hasVolumesToRemove()) {
                    List<URI> removeURIs = param.getRemoveVolumesList().getVolumes();
                    updateFailedVolumeTasks(dbClient, removeURIs, taskId, e);
                }
                throw e;
            }

            updateVolumeAndGroupTasks(dbClient, addVols, removeVols, volumeGroup, taskId);
        }

        /* (non-Javadoc)
         * @see com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#validateUpdateVolumesInVolumeGroup(com.emc.storageos.db.client.DbClient, com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup)
         */
        @Override
        public void validateUpdateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup) {
            // TODO Auto-generated method stub

        }

        protected void updateVolumeAndGroupTasks(DbClient dbClient, List<Volume> addVols, List<Volume> removeVols, VolumeGroup volumeGroup, String taskId) {
            if (addVols != null && !addVols.isEmpty() ) {
                updateVolumeTasks(dbClient, addVols, taskId);
            }
            if (removeVols != null && !removeVols.isEmpty()) {
                updateVolumeTasks(dbClient, removeVols, taskId);
            }
            Operation op = volumeGroup.getOpStatus().get(taskId);
            op.ready();
            volumeGroup.getOpStatus().updateTaskStatus(taskId, op);
            dbClient.updateObject(volumeGroup);
        }

        protected void updateVolumeTasks(DbClient dbClient, List<Volume> vols, String taskId) {
            for (Volume vol : vols) {
                vol = dbClient.queryObject(Volume.class, vol.getId());
                Operation op = vol.getOpStatus().get(taskId);
                if (op != null) {
                    op.ready();
                    vol.getOpStatus().updateTaskStatus(taskId, op);
                    dbClient.updateObject(vol);
                }
            }
        }

    }

    private static class DRVolumeGroupUtils extends VolumeGroupUtils {

        /* (non-Javadoc)
         * @see com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#updateVolumesInVolumeGroup(com.emc.storageos.db.client.DbClient, java.net.URI, com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup, java.lang.String, com.emc.storageos.model.TaskList)
         */
        @Override
        public void updateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup, String taskId,
                TaskList taskList) {
            // TODO Auto-generated method stub

        }

        /* (non-Javadoc)
         * @see com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#validateUpdateVolumesInVolumeGroup(com.emc.storageos.db.client.DbClient, com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup)
         */
        @Override
        public void validateUpdateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup) {
            // TODO Auto-generated method stub

        }

    }

    private static class CopyVolumeGroupUtils extends VolumeGroupUtils {

        private List<Volume> removeVols;
        private List<Volume> addVols;
        private Set<URI> impactedCGs = new HashSet<URI>();
        private Volume firstVol;
        private boolean validated;

        /* (non-Javadoc)
         * @see com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#validateUpdateVolumesInVolumeGroup(com.emc.storageos.db.client.DbClient, com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup, java.lang.String, com.emc.storageos.model.TaskList)
         */
        @Override
        public void validateUpdateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup) {
            impactedCGs = new HashSet<URI>();

            if (param.hasVolumesToAdd()) {
                addVols = validateAddVolumes(dbClient, param, volumeGroup, impactedCGs);
                firstVol = addVols.get(0);
            }
            if (param.hasVolumesToRemove()) {
                List<URI> removeVolList = param.getRemoveVolumesList().getVolumes();
                removeVols = validateRemoveVolumes(dbClient, removeVolList, volumeGroup, impactedCGs);
                if (!removeVols.isEmpty() && firstVol == null) {
                    firstVol = removeVols.get(0);
                }
            }
            validated = true;
        }

        /* (non-Javadoc)
         * @see com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#updateVolumesInVolumeGroup(java.net.URI, com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup, java.lang.String, com.emc.storageos.model.TaskList)
         */
        @Override
        public void updateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup, String taskId, TaskList taskList) {

            if (!validated) {
                validateUpdateVolumesInVolumeGroup(dbClient, param, volumeGroup);
            }

            if (removeVols != null && !removeVols.isEmpty()) {
                // if any of the remove volumes are not in a CG, just update the database
                // this shouldn't happen but it will add robustness if anything goes wrong
                List<Volume> checkVols = new ArrayList<Volume>(removeVols);
                removeVols.clear();
                for (Volume removeVol : checkVols) {
                    if (NullColumnValueGetter.isNullURI(removeVol.getConsistencyGroup())) {
                        removeVol.getVolumeGroupIds().remove(volumeGroup.getId().toString());
                        dbClient.updateObject(removeVol);
                    } else {
                        removeVols.add(removeVol);
                    }
                }
            }

            if ((addVols == null || addVols.isEmpty()) && (removeVols == null || removeVols.isEmpty())) {
                // no volumes to add or remove
                return;
            }

            BlockServiceApi serviceAPI = getBlockService(dbClient, firstVol);
            Operation op = dbClient.createTaskOpStatus(VolumeGroup.class, volumeGroup.getId(),
                    taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
            try {
                taskList.getTaskList().add(toTask(volumeGroup, taskId, op));
                addTasksForVolumesAndCGs(dbClient, addVols, removeVols, impactedCGs, taskId, taskList);
                serviceAPI.updateVolumesInVolumeGroup(param.getAddVolumesList(), removeVols, volumeGroup.getId(), taskId);
            }  catch (InternalException | APIException e) {
                VolumeGroup app = dbClient.queryObject(VolumeGroup.class, volumeGroup.getId());
                op = app.getOpStatus().get(taskId);
                op.error(e);
                app.getOpStatus().updateTaskStatus(taskId, op);
                dbClient.updateObject(app);
                if (param.hasVolumesToAdd()) {
                    List<URI> addURIs = param.getAddVolumesList().getVolumes();
                    updateFailedVolumeTasks(dbClient, addURIs, taskId, e);
                }
                if (param.hasVolumesToRemove()) {
                    List<URI> removeURIs = param.getRemoveVolumesList().getVolumes();
                    updateFailedVolumeTasks(dbClient, removeURIs, taskId, e);
                }
                if (!impactedCGs.isEmpty()) {
                    updateFailedCGTasks(dbClient, impactedCGs, taskId, e);
                }
                throw e;
            }
        }
        
        /**
         * gets the list of replication group names associated with this COPY type volume group
         * @return list of replication group names or empty list if the volume group is not COPY or no volumes exist in 
         * the volume group
         */
        public static Set<String> getReplicationGroupNames(VolumeGroup group, DbClient dbClient) {
            
            Set<String> groupNames = new HashSet<String>();
            if (group.getRoles().contains(VolumeGroup.VolumeGroupRole.COPY.toString())){
                List<Volume> volumes = getVolumeGroupVolumes(dbClient, group);
                if (volumes != null && !volumes.isEmpty()) {
                    BlockServiceApi serviceAPI = getBlockService(dbClient, volumes.iterator().next());
                    groupNames.addAll(serviceAPI.getReplicationGroupNames(group));
                }
            }
            return groupNames;
        }

        /**
         * Validate the volumes to be added to the volume group.
         * For role COPY:
         * All volumes should be the same type (block, or RP, or VPLEX, or SRDF),
         * If the volumes are not in a consistency group, it should specify a CG that the volumes to be add to
         *
         * @param volumes
         * @return The validated volumes
         */
        private List<Volume> validateAddVolumes(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup, Set<URI> impactedCGs) {
            String addedVolType = null;
            String firstVolLabel = null;
            List<URI> addVolList = param.getAddVolumesList().getVolumes();
            URI paramCG = param.getAddVolumesList().getConsistencyGroup();
            List<Volume> volumes = new ArrayList<Volume>();
            for (URI volUri : addVolList) {
                ArgValidator.checkFieldUriType(volUri, Volume.class, "id");
                Volume volume = dbClient.queryObject(Volume.class, volUri);
                if (volume == null || volume.getInactive()) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(), "the volume has been deleted");
                }
                URI cgUri = volume.getConsistencyGroup();
                if (NullColumnValueGetter.isNullURI(cgUri)) {
                     if (paramCG == null) {
                         throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                                 "consistency group is not specified for the volumes not in a consistency group");
                     }
                     ArgValidator.checkFieldUriType(paramCG, BlockConsistencyGroup.class, "consistency_group");

                    BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, paramCG);
                    if (cg == null || cg.getInactive()) {
                        throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                                "consistency group does not exist or has been deleted");
                    }

                     // this volume will be added to a CG as it's put in the application
                     // check to make sure there are no other volumes in that CG that are either not in a
                     // application or in a different application
                     URIQueryResultList uriQueryResultList = new URIQueryResultList();
                     dbClient.queryByConstraint(getVolumesByConsistencyGroup(paramCG), uriQueryResultList);
                     Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class, uriQueryResultList);
                     while (volumeIterator.hasNext()) {
                         Volume otherVolInCg = volumeIterator.next();

                         // skip if the volume is on the add list
                         if (addVolList.contains(otherVolInCg.getId())) {
                             continue;
                         }

                         // fail if the volume is not a member of this application
                         if (otherVolInCg.getVolumeGroupIds() == null || !otherVolInCg.getVolumeGroupIds().contains(volumeGroup.getId().toString())) {
                             throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                                     "Another volume in the same consistency group is not part of the application");
                         }
                     }

                     impactedCGs.add(paramCG);
                }
                URI systemUri = volume.getStorageController();
                StorageSystem system = dbClient.queryObject(StorageSystem.class, systemUri);
                String type = system.getSystemType();
                if (!ALLOWED_SYSTEM_TYPES.contains(type)) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                            "The storage system type that the volume created in is not allowed ");
                }
                String volType = getVolumeType(type);
                if (addedVolType == null) {
                    addedVolType = volType;
                    firstVolLabel = volume.getLabel();
                }
                if (!volType.equals(addedVolType)) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                            "The volume type is not same as others");
                }

                // check to make sure this volume is not part of another application
                StringSet volumeGroups = volume.getVolumeGroupIds();
                List<String> badVolumeGroups = new ArrayList<String>();
                if (volumeGroups != null && !volumeGroups.isEmpty()) {
                    for (String vgId : volumeGroups) {
                        VolumeGroup vg = dbClient.queryObject(VolumeGroup.class, URI.create(vgId));
                        if (vg == null || vg.getInactive()) {
                            // this means the volume points to a non-existent volume group;
                            // this shouldn't happen but we can clean this dangling reference up
                            badVolumeGroups.add(vgId);
                        } else {
                            if (vg.getRoles().contains(VolumeGroup.VolumeGroupRole.COPY.toString())) {
                                throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                                        String.format("The volume is a member of another application: %s", vg.getLabel()));
                            }
                        }
                    }
                    if (!badVolumeGroups.isEmpty()) {
                        for (String vgId : badVolumeGroups) {
                            volume.getVolumeGroupIds().remove(vgId);
                        }
                        dbClient.updateObject(volume);
                        volume = dbClient.queryObject(Volume.class, volume.getId());
                    }
                }
                volumes.add(volume);
            }
            // Check if the to-add volumes are the same volume type as existing volumes in the application
            List<Volume> existingVols = getVolumeGroupVolumes(dbClient, volumeGroup);
            if (!existingVols.isEmpty()) {
                Volume firstVolume = existingVols.get(0);
                URI systemUri = firstVolume.getStorageController();
                StorageSystem system = dbClient.queryObject(StorageSystem.class, systemUri);
                String type = system.getSystemType();
                String existingType = getVolumeType(type);
                if (!existingType.equals(addedVolType)) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(firstVolLabel,
                            "The volume type is not same as existing volumes in the application");
                }
            }
            return volumes;
        }

        /**
         * Valid the volumes to be removed from the volume group. Called by updateVolumeGroup()
         *
         * @param volumes the volumes to be removed from volume group
         * @param volumeGroup The volume group
         * @return The validated volumes
         */
        private List<Volume> validateRemoveVolumes(DbClient dbClient, List<URI> volumes, VolumeGroup volumeGroup, Set<URI> removeVolumeCGs) {
            List<Volume> removeVolumes = new ArrayList<Volume>();
            for (URI voluri : volumes) {
                ArgValidator.checkFieldUriType(voluri, Volume.class, "id");
                Volume vol = dbClient.queryObject(Volume.class, voluri);
                if (vol == null || vol.getInactive()) {
                    log.warn(String.format("The volume [%s] will not be removed from application %s because it does not exist or has been deleted", voluri.toString(), volumeGroup.getLabel()));
                    continue;
                }
                StringSet volumeGroups = vol.getVolumeGroupIds();
                if (volumeGroups == null || !volumeGroups.contains(volumeGroup.getId().toString())) {
                    log.warn(String.format("The volume %s will not be removed from application %s because it is not assigned to the application", vol.getLabel(), volumeGroup.getLabel()));
                    continue;
                }

                if (!NullColumnValueGetter.isNullURI(vol.getConsistencyGroup()) && !isVPlexVolume(vol, dbClient)) {
                    removeVolumeCGs.add(vol.getConsistencyGroup());
                }

                removeVolumes.add(vol);
            }
            return removeVolumes;
        }

        /**
         * Get Volume type, either block, rp, vplex or srdf
         *
         * @param type The system type
         * @return
         */
        private static String getVolumeType(String type) {
            if (BLOCK_TYPES.contains(type)) {
                return BLOCK;
            } else {
                return type;
            }
        }

        private void updateFailedCGTasks(DbClient dbClient, Set<URI> uriList, String taskId, ServiceCoded e) {
            for (URI uri : uriList) {
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, uri);
                Operation op = cg.getOpStatus().get(taskId);
                if (op != null) {
                    op.error(e);
                    cg.getOpStatus().updateTaskStatus(taskId, op);
                    dbClient.updateObject(cg);
                }
            }
        }

        private static BlockServiceApi getBlockService(DbClient dbClient, final Volume volume) {
            URI systemUri = volume.getStorageController();
            StorageSystem system = dbClient.queryObject(StorageSystem.class, systemUri);
            String type = system.getSystemType();
            String volType = getVolumeType(type);
            return getBlockServiceImpl(volType);
        }

    }

    /**
     * Get volume group volumes
     *
     * @param volumeGroup
     * @return The list of volumes in volume group
     */
    private static List<Volume> getVolumeGroupVolumes(DbClient dbClient, VolumeGroup volumeGroup) {
        List<Volume> result = new ArrayList<Volume>();
        final List<Volume> volumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Volume.class,
                        AlternateIdConstraint.Factory.getVolumesByVolumeGroupId(volumeGroup.getId().toString()));
        for (Volume vol : volumes) {
            // TODO return only visible volumes. i.e skip backend or internal volumes?
            if (!vol.getInactive()) {
                result.add(vol);
            }
        }
        return result;
    }

    /**
     * get the children for this volume group
     *
     * @param dbClient
     *            db client for db queries
     * @param volumeGroup
     *            volume group to get children for
     * @return a list of volume groups
     */
    private static List<VolumeGroup> getVolumeGroupChildren(DbClient dbClient, VolumeGroup volumeGroup) {
        List<VolumeGroup> result = new ArrayList<VolumeGroup>();
        final List<VolumeGroup> volumeGroups = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, VolumeGroup.class,
                ContainmentConstraint.Factory.getVolumesGroupsByVolumeGroupId(volumeGroup.getId()));
        for (VolumeGroup volGroup : volumeGroups) {
            result.add(volGroup);
        }
        return result;
    }

    /**
     * Creates and returns an instance of the block full copy manager to handle
     * a full copy request.
     *
     * @return BlockFullCopyManager
     */
    private BlockFullCopyManager getFullCopyManager() {
        BlockFullCopyManager fcManager = new BlockFullCopyManager(_dbClient,
                _permissionsHelper, _auditMgr, _coordinator, _placementManager, sc, uriInfo,
                _request, null);
        return fcManager;
    }

    /**
     * Verifies that the passed replica URI and ensure that it
     * represents a replica for a volume in the passed list of
     * volumes, which are the volumes for a specific volume
     * group.
     *
     * @param replica the replica (Clone/Snapshot/Mirror)
     * @param volumeGroupVolumes the volume group's volumes
     * @return The URI of the replica's source.
     */
    private URI verifyReplicaForCopyRequest(BlockObject replica, List<Volume> volumeGroupVolumes) {
        URI sourceURI = null;
        String replicaType = null;
        if (replica instanceof BlockSnapshot) {
            sourceURI = ((BlockSnapshot) replica).getParent().getURI();
            replicaType = "Snapshot";
        } else if (replica instanceof BlockMirror) {
            sourceURI = ((BlockMirror) replica).getSource().getURI();
            replicaType = "Continuous copy";
        } else if (replica instanceof Volume) {
            sourceURI = ((Volume) replica).getAssociatedSourceVolume();
            replicaType = "Full copy";
        }

        if (NullColumnValueGetter.isNullURI(sourceURI)) {
            throw APIException.badRequests
                    .replicaOperationNotAllowedNotAReplica(replicaType, replica.getLabel());
        }

        // Verify the source is in the volume group.
        boolean sourceInVolumeGroup = false;
        for (Volume volume : volumeGroupVolumes) {
            if (volume.getId().equals(sourceURI)) {
                sourceInVolumeGroup = true;
                break;
            }
        }
        if (!sourceInVolumeGroup) {
            throw APIException.badRequests
                    .replicaOperationNotAllowedSourceNotInVolumeGroup(replicaType, replica.getLabel());
        }
        return sourceURI;
    }

    /**
     * Check if the application has any pending task
     * 
     * @param application
     */
    private void checkForApplicationPendingTasks(VolumeGroup volumeGroup) {
        List<Task> newTasks = TaskUtils.findResourceTasks(_dbClient, volumeGroup.getId());
        for (Task task : newTasks) {
            if (task != null && !task.getInactive() && task.isPending()) {
                throw APIException.badRequests.cannotExecuteOperationWhilePendingTask(volumeGroup.getLabel());
            }
        }
    }

    private String setParent(VolumeGroup volumeGroup, String parent) {
        String errorMsg = null;
        // add parent if specified
        if (parent != null && !parent.isEmpty()) {
            if (URIUtil.isValid(parent)) {
                URI parentId = URI.create(parent);
                ArgValidator.checkFieldUriType(parentId, VolumeGroup.class, "parent");
                VolumeGroup parentVG = _dbClient.queryObject(VolumeGroup.class, parentId);
                if (parentVG == null || parentVG.getInactive()) {
                    errorMsg = "The parent volume group does not exist";
                } else {
                    volumeGroup.setParent(parentId);
                }
            } else if (NullColumnValueGetter.isNullValue(parent)) {
                volumeGroup.setParent(NullColumnValueGetter.getNullURI());
            } else {
                List<VolumeGroup> parentVg = CustomQueryUtility
                        .queryActiveResourcesByConstraint(_dbClient, VolumeGroup.class,
                                PrefixConstraint.Factory.getLabelPrefixConstraint(VolumeGroup.class, parent));
                if (parentVg == null || parentVg.isEmpty()) {
                    errorMsg = "The parent volume group does not exist";
                } else {
                    volumeGroup.setParent(parentVg.iterator().next().getId());
                }
            }
        }
        return errorMsg;
    }

    /**
     * Check if the volume is a vplex volume
     * @param volume The volume to be checked
     * @return true or false
     */
    static private boolean isVPlexVolume(Volume volume, DbClient dbClient) {
        boolean result = false;
        URI storageUri = volume.getStorageController();
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, storageUri);
        String systemType = storage.getSystemType();
        if (systemType.equals(DiscoveredDataObject.Type.vplex.name())) {
            result = true;
        }
        return result;
    }
}
