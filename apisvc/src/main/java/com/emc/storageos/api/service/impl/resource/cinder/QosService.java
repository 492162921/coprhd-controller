/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.impl.resource.ResourceService;
import com.emc.storageos.api.service.impl.resource.VirtualPoolService;
import com.emc.storageos.cinder.model.*;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.QosSpecification;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/v2/{tenant_id}/qos-specs")
@DefaultPermissions( readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = {ACL.OWN, ACL.ALL},
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = {ACL.OWN, ACL.ALL})
@SuppressWarnings({ "unchecked", "rawtypes" })
public class QosService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(QosService.class);
    private static final String EVENT_SERVICE_TYPE = "block";
    // QoS recordOperation labels
    public static final String QOS_CREATED_DESCRIPTION = "Quality of Service Created";
    public static final String QOS_UPDATED_DESCRIPTION = "Quality of Service Updated";
    public static final String QOS_DELETED_DESCRIPTION = "Quality of Service Deleted";

    private static final String QOS_CONSUMER = "back-end";
    private static final String QOS_NAME = "specs-"; // with appended Virtual Pool label
    // QoS spec labels
    private static final String SPEC_PROVISIONING_TYPE = "Provisioning Type";
    private static final String SPEC_PROTOCOL = "Protocol";
    private static final String SPEC_DRIVE_TYPE = "Drive Type";
    private static final String SPEC_SYSTEM_TYPE = "System Type";
    private static final String SPEC_MULTI_VOL_CONSISTENCY = "Multi-Volume Consistency";
    private static final String SPEC_RAID_LEVEL = "RAID Level";
    private static final String SPEC_EXPENDABLE = "Expendable";
    private static final String SPEC_MAX_SAN_PATHS = "Maximum SAN paths";
    private static final String SPEC_MIN_SAN_PATHS = "Minimum SAN paths";
    private static final String SPEC_MAX_BLOCK_MIRRORS = "Maximum block mirrors";
    private static final String SPEC_PATHS_PER_INITIATOR = "Paths per Initiator";
    private static final String SPEC_HIGH_AVAILABILITY = "High Availability";
    private static final String SPEC_MAX_SNAPSHOTS = "Maximum Snapshots";

    private static final String LABEL_DISABLED_SNAPSHOTS = "disabled";
    private static final String LABEL_UNLIMITED_SNAPSHOTS = "unlimited";
    private static final String LABEL_RAID_LEVEL = "raid_level";

    public static final Integer UNLIMITED_SNAPSHOTS = -1;
    public static final Integer DISABLED_SNAPSHOTS = 0;

    /**
     * Create Qos for the given tenant
     *
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param param POST data containing the QoS creation information.
     *
     * @brief Create Qos
     * @return Created QoS specs
     */
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public CinderQosDetail createQoS(@PathParam("tenant_id") String openstack_tenant_id, CinderQosCreateRequest param, @Context HttpHeaders header) {

        _log.info("START create QoS");

        throw new UnsupportedOperationException();
    }

    /**
     * Get the summary list of all Qos for the given tenant
     *     
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant 
     *
     * @brief List Qos
     * @return Qos list
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public CinderQosListRestResp getQosList(@PathParam("tenant_id") String openstack_tenant_id) {
    	CinderQosListRestResp qosListResp= new CinderQosListRestResp();
        _log.info("START get QoS list");

        List<URI> qosSpecsURI = _dbClient.queryByType(QosSpecification.class, true);
        Iterator<QosSpecification> qosIter = _dbClient.queryIterativeObjects(QosSpecification.class, qosSpecsURI);
        while (qosIter.hasNext()) {
            QosSpecification activeQos = qosIter.next();
            if(activeQos != null){
                _log.debug("Qos Specification found, id: {}", activeQos.getId());
                qosListResp.getQos_specs().add(getDataFromQosSpecification(activeQos));
            }
        }

        _log.info("END get QoS list");
        return qosListResp;
    }

    /**
     * Get the details of given Qos for the given tenant
     *
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS
     *
     * @brief List Qos in detail
     * @return Qos detailed list
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public CinderQosDetail getQosDetails(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id) {
        CinderQosDetail qosDetailed = new CinderQosDetail();
        _log.info("START get QoS specs detailed");

        QosSpecification qosSpecification = _dbClient.queryObject(QosSpecification.class, URIUtil.createId(QosSpecification.class, qos_id));
        if(qosSpecification != null){
            _log.debug("Fetched Qos Specification, id: {}", qosSpecification.getId());
            qosDetailed.qos_spec = getDataFromQosSpecification(qosSpecification);
            // Self link points on a Virtual Pool assigned to Qos
            VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, qosSpecification.getVirtualPoolId());
            if(virtualPool != null){
                qosDetailed.setLink(DbObjectMapper.toLink(virtualPool));
            }
        }

        _log.info("END get QoS specs detailed");
        return qosDetailed;
    }

    /**
     * Sets or unsets keys in a specified QoS specification.
     *
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs to update
     *
     * @brief Set or unset key in Qos specs
     * @return Updated Qos specs
     */
    @PUT
    @Path("/{qos_id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public CinderQosDetail setUnsetQosKey(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id, CinderQosKeyUpdateRequest data) {

        _log.info("START set or unset QoS keys");
        throw new UnsupportedOperationException();
    }

    /**
     * Delete Qos for the given tenant
     *
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs to delete
     *
     * @brief Delete Qos specs
     * @return Task result
     */
    @DELETE
    @Path("/{qos_id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public Response deleteQoS(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id, @QueryParam("force") String force) {

        _log.info("START delete QoS, force = {}", force);
        throw new UnsupportedOperationException();
    }

    /**
     * Associates a QoS specification with a specified volume type(virtual pool).
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs
     * @param volume_id the URN of the volume
     *
     * @brief Associates Qos to a Volume Type
     * @return
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}/associate")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public Response associateQosWithVolumeType(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id, @QueryParam("vol_type_id") String vol_type_id) {
        _log.info("START associate qos with volume type(virtual pool)");
        throw new UnsupportedOperationException();
    }

    /**
     * Disassociates a QoS specification from a specified volume type(virtual pool).
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs
     * @param volume_id the URN of the volume
     *
     * @brief Disassociates Qos from a Volume Type
     * @return
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}/disassociate")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public Response disassociateQosFromVolumeType(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id, @QueryParam("vol_type_id") String vol_type_id) {
        _log.info("START disassociate qos from volume type(virtual pool)");
        throw new UnsupportedOperationException();
    }

    /**
     * Disassociates a specified QoS specification from all associations.
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant
     * @param qos_id the URN of the QoS specs
     *
     * @brief Remove all associations for a given Qos specs
     * @return
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}/disassociate_all")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public Response disassociateQosFromAllAssociations(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id) {
        _log.info("START disassociate qos from all associations");
        throw new UnsupportedOperationException();
    }
    
    
	/**
     * Get the detailed list of all associations for a given qos
     *     
     *
     * @prereq none
     *
     * @param tenant_id the URN of the tenant 
     *
     * @brief List volumes in detail
     * @return Volume detailed list
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{qos_id}/associations")
    @CheckPermission( roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = {ACL.ANY})
    public QosAssociationsRestResp getQosAssociations(@PathParam("tenant_id") String openstack_tenant_id, @PathParam("qos_id") String qos_id) {
        _log.info("START get qos associations");
        QosAssociationsRestResp objQosRestResp= new QosAssociationsRestResp();
        _log.info("START get qos associations");

        URI qos_URI = URIUtil.createId(QosSpecification.class, qos_id);
        List<URI> qosSpecs = _dbClient.queryByType(QosSpecification.class, true);
        for (URI spec : qosSpecs) {
            QosSpecification qosSpecification = _dbClient.queryObject(QosSpecification.class,spec);
            if (spec != null && qosSpecification.getId().equals(qos_URI)) {
                objQosRestResp.getAssociation().add(getQosAssociation(qosSpecification));
            }
        }
        _log.info("END get qos association");
        return objQosRestResp;
    }
    

    //INTERNAL FUNCTIONS
    private CinderQos getDataFromQosSpecification(QosSpecification qosSpecs){
        _log.debug("Fetching data from Qos Specification, id: {}", qosSpecs.getId());
        CinderQos qos = new CinderQos();
        // Trim ID to return only UUID
        qos.id = getCinderHelper().trimId(qosSpecs.getId().toString());
        qos.consumer = qosSpecs.getConsumer();
        qos.name = qosSpecs.getName();
        qos.specs = qosSpecs.getSpecs();

        return qos;
    }

    protected CinderHelpers getCinderHelper() {
        return CinderHelpers.getInstance(_dbClient , _permissionsHelper);
    }

    private CinderQosAssociation getQosAssociation(QosSpecification qosSpecs) {
        _log.debug("Fetching data from Qos Specification, id: {}" + qosSpecs.getId());
        CinderQosAssociation cinderQosAssociation = new CinderQosAssociation();
        cinderQosAssociation.name = qosSpecs.getLabel();
        cinderQosAssociation.id = getCinderHelper().trimId(qosSpecs.getVirtualPoolId().toString());
        cinderQosAssociation.association_type = "volume_type";
        return cinderQosAssociation;
    }


    /**
     * Retrieves information from given Virtual Pool, creates and persist Qos object to the DB
     *
     * @param virtualPool Virtual Pool
     * @return QosSpecification filled with information from Virtual Pool
     */
    public static QosSpecification createQosSpecification(VirtualPool virtualPool, DbClient dbClient) {
        _log.debug("Fetching data from Virtual Pool, id: {}", virtualPool.getId());
        QosSpecification qosSpecification = new QosSpecification();
        StringMap specs = new StringMap();
        String systems = virtualPool.getProtocols().toString();
        qosSpecification.setName(QOS_NAME + virtualPool.getLabel());
        qosSpecification.setConsumer(QOS_CONSUMER);
        qosSpecification.setLabel(virtualPool.getLabel());
        qosSpecification.setId(URIUtil.createId(QosSpecification.class));
        qosSpecification.setVirtualPoolId(virtualPool.getId());
        specs.put(SPEC_PROVISIONING_TYPE, virtualPool.getSupportedProvisioningType());
        specs.put(SPEC_PROTOCOL, systems.substring(1, systems.length() - 1));
        specs.put(SPEC_DRIVE_TYPE, virtualPool.getDriveType());
        specs.put(SPEC_SYSTEM_TYPE, VirtualPoolService.getSystemType(virtualPool));
        specs.put(SPEC_MULTI_VOL_CONSISTENCY, Boolean.toString(virtualPool.getMultivolumeConsistency()));
        if (virtualPool.getArrayInfo().get(LABEL_RAID_LEVEL) != null) {
            specs.put(SPEC_RAID_LEVEL, virtualPool.getArrayInfo().get(LABEL_RAID_LEVEL).toString());
        }
        specs.put(SPEC_EXPENDABLE, Boolean.toString(virtualPool.getExpandable()));
        specs.put(SPEC_MAX_SAN_PATHS, Integer.toString(virtualPool.getNumPaths()));
        specs.put(SPEC_MIN_SAN_PATHS, Integer.toString(virtualPool.getMinPaths()));
        specs.put(SPEC_MAX_BLOCK_MIRRORS, Integer.toString(virtualPool.getMaxNativeContinuousCopies()));
        specs.put(SPEC_PATHS_PER_INITIATOR, Integer.toString(virtualPool.getPathsPerInitiator()));
        if (virtualPool.getHighAvailability() != null) {
            specs.put(SPEC_HIGH_AVAILABILITY, virtualPool.getHighAvailability());
        }
        if (virtualPool.getMaxNativeSnapshots().equals(UNLIMITED_SNAPSHOTS)) {
            specs.put(SPEC_MAX_SNAPSHOTS, LABEL_UNLIMITED_SNAPSHOTS);
        }else if(virtualPool.getMaxNativeSnapshots().equals(DISABLED_SNAPSHOTS)){
            specs.put(SPEC_MAX_SNAPSHOTS, LABEL_DISABLED_SNAPSHOTS);
        }else{
            specs.put(SPEC_MAX_SNAPSHOTS, Integer.toString(virtualPool.getMaxNativeSnapshots()));
        }

        qosSpecification.setSpecs(specs);

        // Create new QoS in the DB
        dbClient.createObject(qosSpecification);
        //recordOperation(OperationTypeEnum.CREATE_QOS, QOS_CREATED_DESCRIPTION, qosSpecification);

        return qosSpecification;
    }

    /**
     * Update QoS specification associated with provided VirtualPool.
     *
     * @param vpool the VirtualPool object with updated data.
     * @param qosSpecification the QosSpecification to update.
     */
    public static QosSpecification updateQos(VirtualPool virtualPool, QosSpecification qosSpecification, DbClient dbClient) {
        _log.debug("Updating Qos Specification, id: " + qosSpecification.getId());
        StringMap specs = qosSpecification.getSpecs();
        String systems = virtualPool.getProtocols().toString();
        if (!qosSpecification.getLabel().equals(virtualPool.getLabel())) {
            qosSpecification.setLabel(virtualPool.getLabel());
        }
        if (!qosSpecification.getName().equals(QOS_NAME + virtualPool.getLabel())) {
            qosSpecification.setName(QOS_NAME + virtualPool.getLabel());
        }
        if (!specs.get(SPEC_PROVISIONING_TYPE).equals(virtualPool.getSupportedProvisioningType())) {
            specs.put(SPEC_PROVISIONING_TYPE, virtualPool.getSupportedProvisioningType());
        }
        if (!specs.get(SPEC_PROTOCOL).equals(systems.substring(1, systems.length() - 1))) {
            specs.put(SPEC_PROTOCOL, systems.substring(1, systems.length() - 1));
        }
        if (!specs.get(SPEC_DRIVE_TYPE).equals(virtualPool.getDriveType())) {
            specs.put(SPEC_DRIVE_TYPE, virtualPool.getDriveType());
        }
        if (!specs.get(SPEC_SYSTEM_TYPE).equals(VirtualPoolService.getSystemType(virtualPool))) {
            specs.put(SPEC_SYSTEM_TYPE, VirtualPoolService.getSystemType(virtualPool));
        }
        if (!specs.get(SPEC_MULTI_VOL_CONSISTENCY).equals(Boolean.toString(virtualPool.getMultivolumeConsistency()))) {
            specs.put(SPEC_MULTI_VOL_CONSISTENCY, Boolean.toString(virtualPool.getMultivolumeConsistency()));
        }
        if (virtualPool.getArrayInfo().get(LABEL_RAID_LEVEL) != null
                && !specs.get(SPEC_RAID_LEVEL).equals(virtualPool.getArrayInfo().get(LABEL_RAID_LEVEL).toString())) {
            specs.put(SPEC_RAID_LEVEL, virtualPool.getArrayInfo().get(LABEL_RAID_LEVEL).toString());
        }
        if (!specs.get(SPEC_EXPENDABLE).equals(Boolean.toString(virtualPool.getExpandable()))) {
            specs.put(SPEC_EXPENDABLE, Boolean.toString(virtualPool.getExpandable()));
        }
        if (!specs.get(SPEC_MAX_SAN_PATHS).equals(Integer.toString(virtualPool.getNumPaths()))) {
            specs.put(SPEC_MAX_SAN_PATHS, Integer.toString(virtualPool.getNumPaths()));
        }
        if (!specs.get(SPEC_MIN_SAN_PATHS).equals(Integer.toString(virtualPool.getMinPaths()))) {
            specs.put(SPEC_MIN_SAN_PATHS, Integer.toString(virtualPool.getMinPaths()));
        }
        if (!specs.get(SPEC_MAX_BLOCK_MIRRORS).equals(Integer.toString(virtualPool.getMaxNativeContinuousCopies()))) {
            specs.put(SPEC_MAX_BLOCK_MIRRORS, Integer.toString(virtualPool.getMaxNativeContinuousCopies()));
        }
        if (!specs.get(SPEC_PATHS_PER_INITIATOR).equals(Integer.toString(virtualPool.getPathsPerInitiator()))) {
            specs.put(SPEC_PATHS_PER_INITIATOR, Integer.toString(virtualPool.getPathsPerInitiator()));
        }
        if (virtualPool.getHighAvailability() != null && !specs.get(SPEC_HIGH_AVAILABILITY).equals(virtualPool.getHighAvailability())) {
            specs.put(SPEC_HIGH_AVAILABILITY, virtualPool.getHighAvailability());
        }
        if (!specs.get(SPEC_MAX_SNAPSHOTS).equals(LABEL_UNLIMITED_SNAPSHOTS) && virtualPool.getMaxNativeSnapshots().equals(UNLIMITED_SNAPSHOTS)) {
            specs.put(SPEC_MAX_SNAPSHOTS, LABEL_UNLIMITED_SNAPSHOTS);
        } else if (!specs.get(SPEC_MAX_SNAPSHOTS).equals(LABEL_DISABLED_SNAPSHOTS) && virtualPool.getMaxNativeSnapshots().equals(DISABLED_SNAPSHOTS)) {
            specs.put(SPEC_MAX_SNAPSHOTS, LABEL_DISABLED_SNAPSHOTS);
        } else if (!specs.get(SPEC_MAX_SNAPSHOTS).equals(Integer.toString(virtualPool.getMaxNativeSnapshots()))) {
            specs.put(SPEC_MAX_SNAPSHOTS, Integer.toString(virtualPool.getMaxNativeSnapshots()));
        }

        dbClient.updateObject(qosSpecification);
        //recordOperation(OperationTypeEnum.UPDATE_QOS, QOS_UPDATED_DESCRIPTION, qosSpecification);

        return qosSpecification;
    }

    /**
     * Get QoS specification associated with provided VirtualPool.
     *
     * @param vpoolId the VirtualPool for which QoS specification is required.
     */
    public static QosSpecification getQos(URI vpoolId, DbClient dbClient) throws APIException {
        List<URI> qosSpecsURI = dbClient.queryByType(QosSpecification.class, true);
        Iterator<QosSpecification> qosIter = dbClient.queryIterativeObjects(QosSpecification.class, qosSpecsURI);
        while (qosIter.hasNext()) {
            QosSpecification activeQos = qosIter.next();
            if(activeQos != null && activeQos.getVirtualPoolId().equals(vpoolId)){
                _log.debug("Qos Specification {} assigned to Virtual Pool {} found", activeQos.getId(), vpoolId);
                return activeQos;
            }
        }
        throw APIException.internalServerErrors.noAssociatedQosForVirtualPool(vpoolId);
    }

    static String date(Long timeInMillis){
    	return new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(new java.util.Date (timeInMillis));
    }


    @Override
    protected URI getTenantOwner(URI id) {
        Volume volume = (Volume) queryResource(id);
        return volume.getTenant().getURI();
    }

    /**
     * Volume is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType(){
        return ResourceTypeEnum.VOLUME;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }


    /**
     * Get object specific permissions filter
     *
     */
    @Override
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
                                                                             PermissionsHelper permissionsHelper)
    {
        return new ProjOwnedResRepFilter(user, permissionsHelper, Volume.class);
    }

	@Override
	protected DataObject queryResource(URI id) {
		throw new UnsupportedOperationException();
	}

    public void recordOperation(OperationTypeEnum opType, String evDesc, Object... extParam) {
        String evType;
        evType = opType.getEvType(true);

        _log.info("opType: {} detail: {}", opType.toString(), evType + ':' + evDesc);

        QosSpecification qosSpecification = (QosSpecification) extParam[0];

        StringBuilder specs = new StringBuilder();
        if(qosSpecification.getSpecs() != null){
            for(Map.Entry<String, String> entry : qosSpecification.getSpecs().entrySet()){
                specs.append(" ");
                specs.append(entry.getKey()).append(":").append(entry.getValue());
            }
        }

        switch (opType) {
            case CREATE_QOS:
                auditOp(opType, true, null, qosSpecification.getId().toString(), qosSpecification.getLabel(),
                        qosSpecification.getConsumer(), specs.toString());
                break;
            case UPDATE_QOS:
                auditOp(opType, true, null, qosSpecification.getId().toString(), qosSpecification.getLabel(),
                        qosSpecification.getConsumer(), specs.toString());
                break;
            case DELETE_QOS:
                auditOp(opType, true, null, qosSpecification.getId().toString(), qosSpecification.getLabel());
                break;
            default:
                _log.error("unrecognized qos operation type");
        }

    }

}
