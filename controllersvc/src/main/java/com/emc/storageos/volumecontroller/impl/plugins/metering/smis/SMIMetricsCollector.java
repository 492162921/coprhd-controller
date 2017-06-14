/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.smis;

import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPerformanceResponse;
import com.google.common.collect.ArrayListMultimap;

public class SMIMetricsCollector {
	private static final String DEFAULT_PROVIDER_TIME = "19691231190000.000000-300";
    private static final Logger log = LoggerFactory.getLogger(SMIMetricsCollector.class);
    
    private PortMetricsProcessor portMetricsProcessor;

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        this.portMetricsProcessor = portMetricsProcessor;
    }

    /**
     * Collect metrics.
     *
     * @param system the system
     * @param dbClient the db client
     * @throws Exception
     */
    public void collectMetrics(StorageSystem system, DbClient dbClient) throws Exception {
    	//collect all the statistics necessary for the VMAX port selection procedure.
    	boolean isVmax = StorageSystem.Type.vmax.name().equals(system.getSystemType());
    	
        List<StoragePort> systemPorts = ControllerUtils.getSystemPortsOfSystem(dbClient, system.getId());
        
        for (StoragePort port : systemPorts) {
            // Step 4: if port in db is null just continue.
            if (null == port) {
                continue;
            } else if (!port.getInactive()){	// && metrics[0].endsWith(port.getPortName())) {
                // Step 5: Check whether provider returned port
                // exists in db or not. if port exists in db,
                // then create a PortStat object for it.
            	Stat portStat = new Stat();
                Long kbytes = 10000L;
                Long iops = 100L;
                String statisticTime = "20170614113630.666666+000";
                //write code to get the above values from SRM.
                
                portMetricsProcessor.processFEPortMetrics(kbytes, iops, port, convertCIMStatisticTime(statisticTime));                        
            }
        }
        
    	
        for (StoragePort port : systemPorts) {
			Double percentBusy = 0.0;
			Long idleTicks = 10L;
			Long cumTicks = 20L;			
			Long iops = 100L;
			String statisticTime = "20170614113630.666666+000";
			StorageHADomain haDomain = null;
			
			//write code to get the above values from SRM.
			if (isVmax && system.checkIfVmax3()) {
				// VMAX3 systems only return percent busy directly.
				portMetricsProcessor.processFEAdaptMetrics(percentBusy, iops, haDomain, statisticTime);
			} else {
				portMetricsProcessor.processFEAdaptMetrics(
			         idleTicks, cumTicks, iops, haDomain, statisticTime);
			}
        }
    }
    
    /**
     * Converts the CIM property StatisticTime to msec since the epoch.
     * 
     * @param statisticTime - CIM propertiy in CIM_BlockStatisticalData
     * @return Long time in milliseconds in format similar to System.getMillis()
     */
    public Long convertCIMStatisticTime(String statisticTime) {
        if (statisticTime == null || statisticTime.equals("")
                || statisticTime.equals(DEFAULT_PROVIDER_TIME)) {
            return 0L;
        }
        String[] parts = statisticTime.split("[\\.\\+\\-]");
        Integer year = Integer.parseInt(parts[0].substring(0, 4), 10) - 1900;
        Integer month = Integer.parseInt(parts[0].substring(4, 6), 10) - 1;
        Integer day = Integer.parseInt(parts[0].substring(6, 8), 10);
        Integer hour = Integer.parseInt(parts[0].substring(8, 10), 10);
        Integer min = Integer.parseInt(parts[0].substring(10, 12), 10);
        Integer sec = Integer.parseInt(parts[0].substring(12, 14), 10);
        Integer msec = Integer.parseInt(parts[1].substring(0, 3), 10);
        @SuppressWarnings("deprecation")
        Date date = new Date(year, month, day, hour, min, sec);
        Long millis = date.getTime() + msec;
        date = new Date(millis);
        return millis;
    }

   
}
