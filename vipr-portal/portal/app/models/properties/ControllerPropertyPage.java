/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;

public class ControllerPropertyPage extends CustomPropertyPage {
    private Property meteringEnabled;
    private Property meteringInterval;
    private Property persistMeteringStats;
    private Property monitoringEnabled;
    private Property mdsCloneZoneset;
    private Property mdsAllowZonesetCommit;

    public ControllerPropertyPage(Map<String, Property> properties) {
        super("Controller");
        setRenderTemplate("controllerPage.html");
        meteringEnabled = addCustomProperty(properties, "controller_enable_metering");
        meteringInterval = addCustomProperty(properties, "controller_metering_interval");
        persistMeteringStats = addCustomProperty(properties, "controller_metering_persist_stat_records");
        monitoringEnabled = addCustomProperty(properties, "controller_enable_monitoring");
        mdsCloneZoneset = addCustomProperty(properties, "controller_mds_clone_zoneset");
        mdsAllowZonesetCommit = addCustomProperty(properties, "controller_mds_allow_zoneset_commit");
        		
    }

    public Property getMeteringEnabled() {
        return meteringEnabled;
    }

    public Property getMeteringInterval() {
        return meteringInterval;
    }

    public Property getPersistMeteringStats() {
        return persistMeteringStats;
    }

    public Property getMonitoringEnabled() {
        return monitoringEnabled;
    }
    
    public Property getMdsCloneZoneset() {
    	return mdsCloneZoneset;
    }
    
    public Property getMdsAllowZonesetCommit() {
    	return mdsAllowZonesetCommit;
    }
}
