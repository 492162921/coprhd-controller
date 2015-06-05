/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import java.math.BigDecimal;

import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.licensing.LicenseFeature;
import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.Play;
import play.cache.Cache;
import plugin.StorageOsPlugin;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;

public class LicenseUtils {
    public static final String LICENSE_CACHE_INTERVAL = "2min";

    public static final String CONTROLLER_MODEL = "ViPR_Controller";
    public static final String UNSTRUCTURED_MODEL = "ViPR_Unstructured";

    public static boolean isLicensed(boolean useCache) {
        return isLicensed(LicenseType.CONTROLLER, useCache) || isLicensed(LicenseType.UNSTRUCTURED, useCache);
    }

    public static boolean isControllerLicensed() {
        return isLicensed(LicenseType.CONTROLLER, true);
    }

    public static boolean isObjectLicensed() {
        return isLicensed(LicenseType.UNSTRUCTURED, true);
    }

    public static boolean isCommodityLicensed() {
        return isLicensed(LicenseType.ECS, true) || isLicensed(LicenseType.COMMODITY, true);
    }

    public static boolean isCasLicensed() {
        return isLicensed(LicenseType.CAS, true);
    }

    public static boolean isLicensed(LicenseType type, boolean useCache) {
        if (type == null) {
            return false;
        }
        
        String licensedCacheKey = getLicensedCacheKey(type);
        Boolean licensed = null;
        if (useCache) {
            licensed = (Boolean) Cache.get(licensedCacheKey);
        }
        if (licensed == null) {
            if (StorageOsPlugin.isEnabled()) {
                CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
                licensed = coordinatorClient != null && coordinatorClient.isStorageProductLicensed(type);
            }
            // In Dev mode if we don't have coordinator, assume always licensed
            else if (Play.mode.isDev()) {
                licensed = Boolean.TRUE;
            }
            else {
                licensed = Boolean.FALSE;
            }

            // We don't really want to hit the license check each time.
            Cache.set(licensedCacheKey, licensed, LICENSE_CACHE_INTERVAL);
        }
        return licensed;
    }    
    
    private static String getLicensedCacheKey(LicenseType type) {
        return "license.isLicensed." + type.toString();
    }

    private static void clearLicenseCache() {
        for (LicenseType type: LicenseType.values()) {
            Cache.delete(getLicensedCacheKey(type));
        }
    }

    /**
     * Gets the license from the API client.  If there is any error retrieving the license, this returns null.
     * 
     * @return the license, or null if it could not be retrieved.
     */
    public static License getLicense() {
        try {
            return BourneUtil.getSysClient().license().get();
        }
        catch (RuntimeException e) {
            Logger.error(e, "Could not retrieve license");
            return null;
        }
    }
    
    public static void updateLicenseText(String newLicenseText) {
        BourneUtil.getSysClient().license().set(newLicenseText);
        clearLicenseCache();
    }

    public static boolean hasCapacity(LicenseFeature feature) {
        return LicenseUtils.getLicensedCapacity(feature.getModelId()) != null;
    }

    public static String getLabel(LicenseFeature feature) {
        String modelKey = "license.model." + feature.getModelId();
        String label = MessagesUtils.get(modelKey);
        if (modelKey.equals(label)) {
            label = feature.getModelId();
        }
        return label;
    }

    public static BigDecimal getLicensedCapacity(String model) {
        LicenseFeature licenseFeature = getLicenseFeature(model);
        if (licenseFeature != null && StringUtils.isNotBlank(licenseFeature.getStorageCapacity())) {
            try { 
                
                return new BigDecimal(licenseFeature.getStorageCapacity());
            } catch (NumberFormatException e) {
                // ignore, just return -1
            }
        }
        return null;
    }

    private static LicenseFeature getLicenseFeature(String model) {
        if (StringUtils.isNotBlank(model)) {
            License license = getLicense();
            if (license != null) {
                for (LicenseFeature licenseFeature : license.getLicenseFeatures()) {
                    if (licenseFeature != null && model.equalsIgnoreCase(licenseFeature.getModelId())) {
                        return licenseFeature;
                    }
                }
            }
        }    
        return null;   
    }    
    
}
