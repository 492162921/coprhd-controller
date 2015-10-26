/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteError;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.DistributedAtomicIntegerBuilder;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.uimodels.InitialSetup;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.dr.DRNatCheckParam;
import com.emc.storageos.model.dr.DRNatCheckResponse;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteConfigParam;
import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteErrorResponse;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteParam;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.ExcludeLicenseCheck;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.util.SysUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.sys.ClusterInfo;

/**
 * APIs implementation to standby sites lifecycle management such as add-standby, remove-standby, failover, pause
 * resume replication etc. 
 */
@Path("/site")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class DisasterRecoveryService {
    /* Timeout in minutes for adding standby timeout. If adding state is long than this value, set site to error */
    public static final int STANDBY_ADD_TIMEOUT_MINUTES = 20;

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryService.class);
    
    private static final String SHORTID_FMT="standby%d";
    private static final int MAX_NUM_OF_STANDBY = 10;

    private InternalApiSignatureKeyGenerator apiSignatureGenerator;
    private SiteMapper siteMapper;
    private SysUtils sysUtils;
    private CoordinatorClient coordinator;
    private DbClient dbClient;
    private ScheduledThreadPoolExecutor siteErrorThreadExecutor = new ScheduledThreadPoolExecutor(1);
    private DistributedAtomicIntegerBuilder distributedAtomicIntegerBuilder;
    
    public DisasterRecoveryService() {
        siteMapper = new SiteMapper();
        distributedAtomicIntegerBuilder = new DistributedAtomicIntegerBuilder();
    }
    
    /**
     * Initialize service, this method will be called by Spring after craete DR service instance
     */
    public void initialize(){
        siteErrorThreadExecutor.schedule(new SiteErrorUpdater(null), 0, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Attach one fresh install site to this primary as standby
     * Or attach a primary site for the local standby site when it's first being added.
     * 
     * @param param site detail information
     * @return site response information
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteRestRep addStandby(SiteAddParam param) {
        log.info("Retrieving standby site config from: {}", param.getVip());
        
        String siteId = null;
        
        try {
            VirtualDataCenter vdc = queryLocalVDC();
            List<Site> existingSites = getStandbySites(vdc.getId());

            // parameter validation and precheck
            validateAddParam(param, existingSites);
            ViPRCoreClient viprClient = createViPRCoreClient(param.getVip(), param.getUsername(), param.getPassword());

            SiteConfigRestRep standbyConfig = viprClient.site().getStandbyConfig();
            
            siteId = standbyConfig.getUuid();
            
            precheckForStandbyAttach(standbyConfig);
            
            Site standbySite = new Site();
            standbySite.setCreationTime((new Date()).getTime());
            standbySite.setName(param.getName());
            standbySite.setVdc(vdc.getId());
            standbySite.setVip(param.getVip());
            standbySite.getHostIPv4AddressMap().putAll(new StringMap(standbyConfig.getHostIPv4AddressMap()));
            standbySite.getHostIPv6AddressMap().putAll(new StringMap(standbyConfig.getHostIPv6AddressMap()));
            standbySite.setSecretKey(standbyConfig.getSecretKey());
            standbySite.setUuid(standbyConfig.getUuid());
            String shortId = generateShortId(existingSites);
            standbySite.setStandbyShortId(shortId);
            standbySite.setDescription(param.getDescription());
            standbySite.setState(SiteState.STANDBY_ADDING);
            if (log.isDebugEnabled()) {
                log.debug(standbySite.toString());
            }
            coordinator.addSite(standbyConfig.getUuid());
            log.info("Persist standby site to ZK {}", shortId);
            //coordinator.setTargetInfo(standbySite);
            coordinator.persistServiceConfiguration(standbySite.toConfiguration());
            
            // wake up syssvc to regenerate configurations
            updateVdcTargetVersion(coordinator.getSiteId(), SiteInfo.RECONFIG_RESTART);
            for (Site site : existingSites) {
                updateVdcTargetVersion(site.getUuid(), SiteInfo.RECONFIG_RESTART);
            }

            // reconfig standby site
            log.info("Updating the primary site info to site: {}", standbyConfig.getUuid());
            SiteConfigParam configParam = new SiteConfigParam();
            SiteParam primarySite = new SiteParam();
            primarySite.setHostIPv4AddressMap(new StringMap(vdc.getHostIPv4AddressesMap()));
            primarySite.setHostIPv6AddressMap(new StringMap(vdc.getHostIPv6AddressesMap()));
            primarySite.setName(param.getName()); // this is the name for the standby site
            primarySite.setSecretKey(vdc.getSecretKey());
            primarySite.setUuid(coordinator.getSiteId());
            primarySite.setVip(vdc.getApiEndpoint());
            configParam.setPrimarySite(primarySite);
            
            List<SiteParam> standbySites = new ArrayList<SiteParam>();
            for (Site standby : getStandbySites(vdc.getId())) {
                SiteParam standbyParam = new SiteParam();
                siteMapper.map(standby, standbyParam);
                standbySites.add(standbyParam);
            }
            configParam.setStandbySites(standbySites);
            viprClient.site().syncSite(configParam);
            
            siteErrorThreadExecutor.schedule(new SiteErrorUpdater(standbySite.getUuid()), STANDBY_ADD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            
            return siteMapper.map(standbySite);
        } catch (Exception e) {
            log.error("Internal error for updating coordinator on standby", e);
            InternalServerErrorException addStandbyFailedException = APIException.internalServerErrors.addStandbyFailed(e.getMessage());
            setSiteError(siteId, addStandbyFailedException);
            throw addStandbyFailedException;
        }
    }

    /**
     * Sync all the site information from the primary site to the new standby
     * The current site will be demoted from primary to standby during the process
     * 
     * @param configParam
     * @return
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ExcludeLicenseCheck
    public Response syncSites(SiteConfigParam configParam) {
        log.info("sync sites from primary site");
        
        try {
            // update vdc
            VirtualDataCenter vdc = queryLocalVDC();
            
            SiteParam primary = configParam.getPrimarySite();
            vdc.setApiEndpoint(primary.getVip());
            vdc.getHostIPv4AddressesMap().clear();
            vdc.getHostIPv4AddressesMap().putAll(new StringMap(primary.getHostIPv4AddressMap()));
            vdc.getHostIPv6AddressesMap().clear();
            vdc.getHostIPv6AddressesMap().putAll(new StringMap(primary.getHostIPv6AddressMap()));
            vdc.setSecretKey(primary.getSecretKey());
            int hostCount = primary.getHostIPv4AddressMap().size();
            if (primary.getHostIPv6AddressMap().size() > hostCount) {
                hostCount = primary.getHostIPv6AddressMap().size();
            }
            vdc.setHostCount(hostCount);
            
            coordinator.addSite(primary.getUuid());
            coordinator.setPrimarySite(primary.getUuid());
            
            // Add other standby sites
            for (SiteParam standby : configParam.getStandbySites()) {
                Site site = new Site();
                site.setCreationTime((new Date()).getTime());
                siteMapper.map(standby, site);
                site.setVdc(vdc.getId());
                coordinator.persistServiceConfiguration(site.toConfiguration());
                coordinator.addSite(standby.getUuid());
                log.info("Persist standby site {} to ZK", standby.getVip());
            }
            
            log.info("Persist primary site to DB");
            dbClient.persistObject(vdc);
            
            updateVdcTargetVersionAndDataRevision(SiteInfo.UPDATE_DATA_REVISION);
            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            log.error("Internal error for updating coordinator on standby", e);
            throw APIException.internalServerErrors.configStandbyFailed(e.getMessage());
        }
    }

    /**
     * Get all sites including standby and primary
     * 
     * @return site list contains all sites with detail information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SiteList getSites() {
        log.info("Begin to list all standby sites of local VDC");
        SiteList standbyList = new SiteList();

        VirtualDataCenter vdc = queryLocalVDC();
        for (Site site : getSites(vdc.getId())) {
             standbyList.getSites().add(siteMapper.map(site));
        }
        return standbyList;
    }
    
    /**
     * Get specified site according site UUID
     * 
     * @param uuid site UUID
     * @return site response with detail information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}")
    public SiteRestRep getSite(@PathParam("uuid") String uuid) {
        log.info("Begin to get standby site by uuid {}", uuid);
        
        try {
            Configuration config = coordinator.queryConfiguration(Site.CONFIG_KIND, uuid);
            if (config != null) {
                return siteMapper.map(new Site(config));
            }
        } catch (Exception e) {
            log.error("Find find site from ZK for UUID " + uuid, e);
        }
        
        log.info("Can't find site with specified site ID {}", uuid);
        return null;
    }

    /**
     * Remove a standby. After successfully done, it stops data replication to this site
     * 
     * @param uuid standby site uuid
     * @return
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}")
    public SiteRestRep removeStandby(@PathParam("uuid") String uuid) {
        log.info("Begin to remove standby site from local vdc by uuid: {}", uuid);
        Configuration config = coordinator.queryConfiguration(Site.CONFIG_KIND, uuid);
        if (config == null) {
            log.error("Can't find site {} from ZK", uuid);
            throw APIException.badRequests.siteIdNotFound();
        }

        if (!isClusterStable()) {
            throw APIException.internalServerErrors.removeStandbyFailed(uuid, "Cluster is not stable");
        }

        Site toBeRemovedSite = new Site(config);
        if (toBeRemovedSite.getState().equals(SiteState.PRIMARY)) {
            log.error("Unable to remove this site {}. It is primary", uuid);
            throw APIException.badRequests.operationNotAllowedOnPrimarySite();
        }
        
        try {
            log.info("Find standby site in local VDC and remove it");
            toBeRemovedSite.setState(SiteState.STANDBY_REMOVING);
            coordinator.persistServiceConfiguration(toBeRemovedSite.toConfiguration());

            log.info("Notify all sites for reconfig");
            VirtualDataCenter vdc = queryLocalVDC();
            for (Site standbySite : getSites(vdc.getId())) {
                updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.RECONFIG_RESTART);
            }
            return siteMapper.map(toBeRemovedSite);
        } catch (Exception e) {
            log.error("Failed to remove site {}", uuid, e);
            throw APIException.internalServerErrors.removeStandbyFailed(uuid, e.getMessage());
        }
    }
    
    /**
     * Get standby site configuration
     * 
     * @return SiteRestRep standby site configuration.
     */
    @GET
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/localconfig")
    public SiteConfigRestRep getStandbyConfig() {
        log.info("Begin to get standby config");
        String siteId = coordinator.getSiteId();
        VirtualDataCenter vdc = queryLocalVDC();
        SecretKey key = apiSignatureGenerator.getSignatureKey(SignatureKeyType.INTERVDC_API);
        
        SiteConfigRestRep siteConfigRestRep = new SiteConfigRestRep();
        siteConfigRestRep.setUuid(siteId);
        siteConfigRestRep.setVip(vdc.getApiEndpoint());
        siteConfigRestRep.setSecretKey(new String(Base64.encodeBase64(key.getEncoded()), Charset.forName("UTF-8")));
        siteConfigRestRep.setHostIPv4AddressMap(vdc.getHostIPv4AddressesMap());
        siteConfigRestRep.setHostIPv6AddressMap(vdc.getHostIPv6AddressesMap());
        siteConfigRestRep.setDbSchemaVersion(coordinator.getCurrentDbSchemaVersion());
        siteConfigRestRep.setFreshInstallation(isFreshInstallation());
        siteConfigRestRep.setClusterStable(isClusterStable());
        
        Configuration config = coordinator.queryConfiguration(Site.CONFIG_KIND, coordinator.getSiteId());
        if (config != null) {
            Site site = new Site(config);
            siteConfigRestRep.setState(site.getState().toString());
        } else {
            siteConfigRestRep.setState(SiteState.PRIMARY.toString());
        }
        
        try {
            siteConfigRestRep.setSoftwareVersion(coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion().toString());
        } catch (Exception e) {
            log.error("Fail to get software version {}", e);
        }

        log.info("Return result: {}", siteConfigRestRep);
        return siteConfigRestRep;
    }
    
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/natcheck")
    @ExcludeLicenseCheck
    public DRNatCheckResponse checkIfBehindNat(DRNatCheckParam checkParam, @HeaderParam("X-Forwarded-For") String clientIp) {
        if (checkParam == null) {
            log.error("checkParam is null, X-Forwarded-For is {}", clientIp);
            throw APIException.internalServerErrors.invalidNatCheckCall("(null)", clientIp);
        }

        String ipv4Str = checkParam.getIPv4Address();
        String ipv6Str = checkParam.getIPv6Address();
        log.info(String.format("Performing NAT check, client address connecting to VIP: %s. Client reports its IPv4 = %s, IPv6 = %s",
                clientIp, ipv4Str, ipv6Str));

        boolean isBehindNat = false;
        try {
            isBehindNat = sysUtils.checkIfBehindNat(ipv4Str, ipv6Str, clientIp);
        } catch (Exception e) {
            log.error("Fail to check NAT {}", e);
            throw APIException.internalServerErrors.invalidNatCheckCall(e.getMessage(), clientIp);
        }

        DRNatCheckResponse resp = new DRNatCheckResponse();
        resp.setSeenIp(clientIp);
        resp.setBehindNAT(isBehindNat);

        return resp;
    }

    /**
     * Pause a standby site that is already sync'ed with the primary
     * @param uuid site UUID
     * @return updated standby site representation
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/pause/{uuid}")
    public SiteRestRep pauseStandby(@PathParam("uuid") String uuid) {
        log.info("Begin to pause data sync between standby site from local vdc by uuid: {}", uuid);
        if (!isClusterStable()) {
            log.error("Cluster is unstable");
            throw APIException.serviceUnavailable.clusterStateNotStable();
        }

        Configuration config = coordinator.queryConfiguration(Site.CONFIG_KIND, uuid);
        if (config == null) {
            log.error("Can't find site {} from ZK", uuid);
            throw APIException.badRequests.siteIdNotFound();
        }

        Site standby = new Site(config);
        if (!standby.getState().equals(SiteState.STANDBY_SYNCED)) {
            log.error("site {} is in state {}, should be STANDBY_SYNCED", uuid, standby.getState());
            throw APIException.badRequests.operationOnlyAllowedOnSyncedSite(uuid, standby.getState().toString());
        }

        try {
            standby.setState(SiteState.STANDBY_PAUSED);
            coordinator.persistServiceConfiguration(standby.toConfiguration());

            VirtualDataCenter vdc = queryLocalVDC();

            // exclude the paused site from strategy options of dbsvc and geodbsvc
            String dcId = String.format("%s-%s", vdc.getShortId(), standby.getStandbyShortId());
            ((DbClientImpl)dbClient).getLocalContext().removeDcFromStrategyOptions(dcId);
            ((DbClientImpl)dbClient).getGeoContext().removeDcFromStrategyOptions(dcId);

            for (Site site : getStandbySites(vdc.getId())) {
                updateVdcTargetVersion(site.getUuid(), SiteInfo.RECONFIG_RESTART);
            }

            // update the local(primary) site last
            updateVdcTargetVersion(coordinator.getSiteId(), SiteInfo.RECONFIG_RESTART);

            return siteMapper.map(standby);
        } catch (Exception e) {
            log.error("Error pausing site {}", uuid, e);
            throw APIException.internalServerErrors.pauseStandbyFailed(uuid, e.getMessage());
        }
    }

    /**
     * Query the latest error message for specific standby site
     * 
     * @param uuid site UUID
     * @return site response with detail information
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}/error")
    public SiteErrorResponse getSiteError(@PathParam("uuid") String uuid) {
        log.info("Begin to get site error by uuid {}", uuid);
        
        try {
            Configuration config = coordinator.queryConfiguration(Site.CONFIG_KIND, uuid);
            if (config == null) {
                log.error("Can't find site {} from ZK", uuid);
                throw APIException.badRequests.siteIdNotFound();
            }

            Site standby = new Site(config);
            
            if (standby.getState().equals(SiteState.STANDBY_ERROR)) {
                return coordinator.getTargetInfo(uuid, SiteError.class).toResponse();
            }
        } catch (Exception e) {
            log.error("Find find site from ZK for UUID {} : {}" + uuid, e);
        }
        
        return SiteErrorResponse.noError();
    }
    
    /**
     * This API will do planned failover to target new primary site according passed in site UUID. After failover, old primary site will
     * work as normal standby site and target site will be promoted to primary. All site will update properties to trigger reconfig.
     * 
     * @param uuid target new primary site UUID
     * @return return accepted response if operation is successful
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{uuid}/failover")
    public Response doPlannedFailover(@PathParam("uuid") String uuid) {
        log.info("Begin to failover for standby UUID {}", uuid);

        precheckForPlannedFailover(uuid);
        
        try {
            VirtualDataCenter vdc = queryLocalVDC();
            
            int oldPrimaryHostCount = vdc.getHostCount();
            String oldPrimaryUUID = coordinator.getPrimarySiteId();
            List<Site> existingSites = getStandbySites(vdc.getId());

            // update VDC
            Site newPrimarySite = new Site(coordinator.queryConfiguration(Site.CONFIG_KIND, uuid));
            vdc.setApiEndpoint(newPrimarySite.getVip());
            vdc.getHostIPv4AddressesMap().clear();
            vdc.getHostIPv4AddressesMap().putAll(new StringMap(newPrimarySite.getHostIPv4AddressMap()));
            vdc.getHostIPv6AddressesMap().clear();
            vdc.getHostIPv6AddressesMap().putAll(new StringMap(newPrimarySite.getHostIPv6AddressMap()));
            vdc.setSecretKey(newPrimarySite.getSecretKey());
            int hostCount = newPrimarySite.getHostIPv4AddressMap().size();
            if (newPrimarySite.getHostIPv6AddressMap().size() > hostCount) {
                hostCount = newPrimarySite.getHostIPv6AddressMap().size();
            }
            vdc.setHostCount(hostCount);
            dbClient.persistObject(vdc);

            // Set new UUID as primary site ID
            coordinator.setPrimarySite(uuid);

            // set new primary site to ZK
            newPrimarySite.setState(SiteState.STANDBY_PLANNED_FAILOVERING);
            coordinator.persistServiceConfiguration(newPrimarySite.getUuid(), newPrimarySite.toConfiguration());

            // Set old primary site's state, short id and key
            Site oldPrimarySite = new Site(coordinator.queryConfiguration(Site.CONFIG_KIND, oldPrimaryUUID));
            oldPrimarySite.setStandbyShortId(generateShortId(existingSites));
            oldPrimarySite.setState(SiteState.PRIMARY_PLANNED_FAILOVERING);
            coordinator.persistServiceConfiguration(oldPrimarySite.getUuid(), oldPrimarySite.toConfiguration());
            
            DistributedAtomicInteger daiNewPrimary = distributedAtomicIntegerBuilder.client(coordinator)
                    .siteId(newPrimarySite.getUuid()).path(DistributedAtomicIntegerBuilder.PLANNED_FAILOVER_STANDBY_NODECOUNT).build();
            daiNewPrimary.forceSet(vdc.getHostCount());
            
            DistributedAtomicInteger daiOldPrimary = distributedAtomicIntegerBuilder.client(coordinator)
                    .siteId(oldPrimaryUUID).path(DistributedAtomicIntegerBuilder.PLANNED_FAILOVER_PRIMARY_NODECOUNT).build();
            daiOldPrimary.forceSet(oldPrimaryHostCount);
            
            // trigger local property change to reconfig
            updateVdcTargetVersion(oldPrimaryUUID, SiteInfo.RECONFIG_RESTART);

            // trigger other site property change to reconfig
            for (Site site : existingSites) {
                updateVdcTargetVersion(site.getUuid(), SiteInfo.RECONFIG_RESTART);
            }
        } catch (Exception e) {
            log.error("Failed to do failover {}", e);
        }

        return Response.status(Response.Status.ACCEPTED).build();
    }

    private void updateVdcTargetVersion(String siteId, String action) throws Exception {
        SiteInfo siteInfo;
        SiteInfo currentSiteInfo = coordinator.getTargetInfo(siteId, SiteInfo.class);
        if (currentSiteInfo != null) {
            siteInfo = new SiteInfo(System.currentTimeMillis(), action, currentSiteInfo.getTargetDataRevision());
        } else {
            siteInfo = new SiteInfo(System.currentTimeMillis(), action);
        }
        coordinator.setTargetInfo(siteId, siteInfo);
        log.info("VDC target version updated to {} for site {}", siteInfo.getVdcConfigVersion(), siteId);
    }

    private void updateVdcTargetVersionAndDataRevision(String action) throws Exception {
        int ver = 1;
        SiteInfo siteInfo = coordinator.getTargetInfo(SiteInfo.class);
        if (siteInfo != null) {
            if (!siteInfo.isNullTargetDataRevision()) {
                String currentDataRevision = siteInfo.getTargetDataRevision();
                ver = Integer.valueOf(currentDataRevision);
            }
        }
        String targetDataRevision = String.valueOf(++ver);
        siteInfo = new SiteInfo(System.currentTimeMillis(), action, targetDataRevision);
        coordinator.setTargetInfo(siteInfo);
        log.info("VDC target version updated to {}, revision {}",
                siteInfo.getVdcConfigVersion(), targetDataRevision);
    }
    
    /*
     * Internal method to check whether standby can be attached to current primary site
     */
    protected void precheckForStandbyAttach(SiteConfigRestRep standby) {
        try {
            if (!isClusterStable()) {
                throw new Exception("Current site is not stable");
            }

            if (!standby.isClusterStable()) {
                throw new Exception("Remote site is not stable");
            }

            //standby should be refresh install
            if (!standby.isFreshInstallation()) {
                throw new Exception("Standby is not a fresh installation");
            }
            
            //DB schema version should be same
            String currentDbSchemaVersion = coordinator.getCurrentDbSchemaVersion();
            String standbyDbSchemaVersion = standby.getDbSchemaVersion();
            if (!currentDbSchemaVersion.equalsIgnoreCase(standbyDbSchemaVersion)) {
                throw new Exception(String.format("Standby db schema version %s is not same as primary %s",
                        standbyDbSchemaVersion, currentDbSchemaVersion));
            }
            
            //software version should be matched
            SoftwareVersion currentSoftwareVersion;
            SoftwareVersion standbySoftwareVersion;
            try {
                currentSoftwareVersion = coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion();
                standbySoftwareVersion = new SoftwareVersion(standby.getSoftwareVersion());
            } catch (Exception e) {
                throw new Exception(String.format("Fail to get software version %s", e.getMessage()));
            }
            
            if (!isVersionMatchedForStandbyAttach(currentSoftwareVersion,standbySoftwareVersion)) {
                throw new Exception(String.format("Standby site version %s is not equals to current version %s",
                        standbySoftwareVersion, currentSoftwareVersion));
            }
            
            //this site should not be standby site
            String primaryID = coordinator.getPrimarySiteId();
            if (primaryID != null && !primaryID.equals(coordinator.getSiteId())) {
                throw new Exception("This site is also a standby site");
            }
            
            
        } catch (Exception e) {
            log.error("Standby information can't pass pre-check {}", e.getMessage());
            throw APIException.internalServerErrors.addStandbyPrecheckFailed(e.getMessage());
        }
    }

    /*
     * Internal method to check whether failover from primary to standby is allowed
     */
    protected void precheckForPlannedFailover(String standbyUuid) {
        try {
            Configuration config = coordinator.queryConfiguration(Site.CONFIG_KIND, standbyUuid);
            if (config == null) {
                throw new Exception("Standby uuid is not valid, can't find in ZK");
            }

            Site standby = new Site(config);

            if (standbyUuid.equals(coordinator.getPrimarySiteId())) {
                throw new Exception("Can't failover to a primary site");
            }

            // TODO Need to check standby heartbeat to make sure standby is connected
            // Heartbeat mechanism is not implemented yet, so here just omit for now

            if (!isClusterStable()) {
                throw new Exception("Primary site is not stable");
            }

            if (!isSiteStable(standby)) {
                throw new Exception("Standby site is not stable");
            }

            if (standby.getState() != SiteState.STANDBY_SYNCED) {
                throw new Exception("Standby site is not fully synced");
            }

        } catch (Exception e) {
            log.error(String.format("Failed to failover to site %s", standbyUuid), e);
            InternalServerErrorException failoverException = APIException.internalServerErrors.plannedFailoverPrecheckFailed(standbyUuid, e.getMessage());
            setSiteError(standbyUuid, failoverException);
            throw failoverException;
        }
    }
    
    protected void validateAddParam(SiteAddParam param, List<Site> existingSites) {
        for (Site site : existingSites) {
            if (site.getName().equals(param.getName())) {
                throw APIException.internalServerErrors.addStandbyPrecheckFailed("Duplicate site name");
            }

            int nodeCount = getSiteNodeCount(site);
            ClusterInfo.ClusterState state = coordinator.getControlNodesState(site.getUuid(), nodeCount);
            if (state != ClusterInfo.ClusterState.STABLE) {
                log.info("Site {} is not stable {}", site.getUuid(), state);
                throw APIException.internalServerErrors.addStandbyPrecheckFailed(String.format("Site %s is not stable %s", site.getName(), state));
            }
        }
    }

    private String generateShortId(List<Site> existingSites) throws Exception{
        Set<String> existingShortIds = new HashSet<String>();
        for (Site site : existingSites) {
            existingShortIds.add(site.getStandbyShortId());
        }
        
        for (int i = 1; i < MAX_NUM_OF_STANDBY; i ++) {
            String id = String.format(SHORTID_FMT, i);
            if (!existingShortIds.contains(id)) {
                return id;
            }
        }
        throw new Exception("Failed to generate standby short id");
    }

    private List<Site> getStandbySites(URI vdcId) {
        List<Site> result = new ArrayList<Site>();
        for(Configuration config : coordinator.queryAllConfiguration(Site.CONFIG_KIND)) {
            Site site = new Site(config);
            if (site.getVdc().equals(vdcId) && site.getState() != SiteState.PRIMARY && site.getState() != SiteState.PRIMARY_PLANNED_FAILOVERING) {
                result.add(site);
            }
        }
        return result;
    }
    
    private List<Site> getSites(URI vdcId) {
        List<Site> result = new ArrayList<Site>();
        for(Configuration config : coordinator.queryAllConfiguration(Site.CONFIG_KIND)) {
            Site site = new Site(config);
            if (vdcId.equals(site.getVdc())) {
                result.add(site);
            }
        }
        return result;
    }

    private int getSiteNodeCount(Site site) {
        int ipv4Count = site.getHostIPv4AddressMap().size();
        int ipv6Count = site.getHostIPv6AddressMap().size();
        return ipv4Count > 0? ipv4Count : ipv6Count;
    }

    /*
     * Internal method, fetch cluster state of a specific site from Zookeeper.
     */
    protected boolean isSiteStable(Site site) {
        int nodeCount = getSiteNodeCount(site);
        ClusterInfo.ClusterState state = coordinator.getControlNodesState(site.getUuid(), nodeCount);
        return state == ClusterInfo.ClusterState.STABLE;
    }

    protected boolean isClusterStable() {
        return coordinator.getControlNodesState() == ClusterInfo.ClusterState.STABLE;
    }
    
    protected boolean isFreshInstallation() {
        Configuration setupConfig = coordinator.queryConfiguration(InitialSetup.CONFIG_KIND, InitialSetup.CONFIG_ID);
        
        boolean freshInstall = (setupConfig == null) || !Boolean.parseBoolean(setupConfig.getConfig(InitialSetup.COMPLETE));
        log.info("Fresh installation {}", freshInstall);
        
        boolean hasDataInDB = dbClient.hasUsefulData();
        log.info("Has useful data in DB {}", hasDataInDB);
        
        return freshInstall && !hasDataInDB;
    }
    
    protected boolean isVersionMatchedForStandbyAttach(SoftwareVersion currentSoftwareVersion, SoftwareVersion standbySoftwareVersion) {
        if (currentSoftwareVersion == null || standbySoftwareVersion == null) {
            return false;
        }
        
        String versionString = standbySoftwareVersion.toString();
        SoftwareVersion standbyVersionWildcard = new SoftwareVersion(versionString.substring(0, versionString.lastIndexOf("."))+".*");
        return currentSoftwareVersion.weakEquals(standbyVersionWildcard);
    }

    // encapsulate the create ViPRCoreClient operation for easy UT writing because need to mock ViPRCoreClient
    protected ViPRCoreClient createViPRCoreClient(String vip, String username, String password) {
        return new ViPRCoreClient(vip, true).withLogin(username, password);
    }

    // encapsulate the get local VDC operation for easy UT writing because VDCUtil.getLocalVdc is static method
    protected VirtualDataCenter queryLocalVDC() {
        return VdcUtil.getLocalVdc();
    }
    
    private void setSiteError(String siteId, InternalServerErrorException exception) {
        if (siteId == null || siteId.isEmpty())
            return;
        
        Configuration config = coordinator.queryConfiguration(Site.CONFIG_KIND, siteId);
        if (config != null) {
            Site site = new Site(config);
            site.setState(SiteState.STANDBY_ERROR);
            coordinator.persistServiceConfiguration(siteId, site.toConfiguration());
            
            SiteError error = new SiteError(exception);
            coordinator.setTargetInfo(site.getUuid(), error);
        }
    }

    public InternalApiSignatureKeyGenerator getApiSignatureGenerator() {
        return apiSignatureGenerator;
    }

    public void setApiSignatureGenerator(InternalApiSignatureKeyGenerator apiSignatureGenerator) {
        this.apiSignatureGenerator = apiSignatureGenerator;
    }
    
    public void setSiteMapper(SiteMapper siteMapper) {
        this.siteMapper = siteMapper;
    }

    public void setSysUtils(SysUtils sysUtils) {
        this.sysUtils = sysUtils;
    }
    
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setDistributedAtomicIntegerBuilder(DistributedAtomicIntegerBuilder distributedAtomicIntegerBuilder) {
        this.distributedAtomicIntegerBuilder = distributedAtomicIntegerBuilder;
    }

    class SiteErrorUpdater implements Runnable {
        private String siteId;

        public SiteErrorUpdater(String siteId) {
            this.siteId = siteId;
        }

        @Override
        public void run() {
            log.info("launch site error updater");
            try {
                if (siteId == null) {
                    URI vdcId = queryLocalVDC().getId();
                    List<Site> sites = getSites(vdcId);

                    for (Site site : sites) {
                        setSiteError(site);
                    }
                } else {
                    Configuration config = coordinator.queryConfiguration(Site.CONFIG_KIND, siteId);
                    if (config == null)
                        return;

                    Site site = new Site(config);
                    setSiteError(site);
                }
            } catch (Exception e) {
                log.error("Error occurs during update site errors {}", e);
            }

        }

        private void setSiteError(Site site) {
            if (SiteState.STANDBY_ADDING.equals(site.getState())
                    && (new Date()).getTime() - site.getCreationTime() > (STANDBY_ADD_TIMEOUT_MINUTES * 1000 * 60)) {
                log.info("Site {} is set to error because of adding timeout", site.getName());
                SiteError error = new SiteError(APIException.internalServerErrors.addStandbyFailedTimeout(STANDBY_ADD_TIMEOUT_MINUTES));
                coordinator.setTargetInfo(site.getUuid(), error);

                site.setState(SiteState.STANDBY_ERROR);
                coordinator.persistServiceConfiguration(site.getUuid(), site.toConfiguration());
            }
        }
    }
}
