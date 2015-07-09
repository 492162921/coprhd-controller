/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.licensing;

import java.math.BigInteger;

import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

public final class LicenseConstants {
        
    public static final String MM_DD_YYYY_FORMAT = "MM/dd/yyyy";
    public static final String LICENSE_FILE_PATH = "/tmp/.license";
    // indicators to show what system generated the serial number. SYR will use
    // this to determine if they need
    // to set add the license manually to the IB system.
    protected static final String LAC = "L";
    protected static final String SWID = "U";
    // Valid ViPR Model Id's.
    final static String VIPR_CONTROLLER = "ViPR_Controller";
    final static String VIPR_OBJECT = "ViPR_Object";
    final static String VIPR_HDFS = "ViPR_HDFS";
    final static String VIPR_OBJECTHDFS = "ViPR_ObjectHDFS";
    final static String VIPR_UNSTRUCTURED = "ViPR_Unstructured";
    final static String VIPR_CAS = "ViPR_CAS";
    final static String VIPR_BLOCK = "ViPR_Block";
    final static String VIPR_COMMODITY = "ViPR_Commodity";
    final static String VIPR_ECS = "ViPR_ECS";
    
    // Static strings used as map keys for the vendorstring map. 
    public final static String STORAGE_CAPACITY = "CAPACITY";
    public final static String STORAGE_CAPACITY_UNITS = "CAPACITY_UNIT";
    public final static String SWID_VALUE = "SWID";
    // name value pair for trial license: UNIT=TrialMode
    public final static String TRIAL_LICENSE_NAME = "UNIT";
    
    // Allowed trial license values in ViPR 2.0 trial license and ViPR 1.0 trial license
    public final static String[] TRIAL_LICENSE_VALUE = {"LimitedUseMode", "TrialMode"};
    
    public final static String TERABYTE="TB";
    public final static BigInteger TB_VALUE = new BigInteger("1099511627776");
    // License File regex pattern. This pattern will return the ViPR model number and the value of the VENDOR_STRING
    public static final String LICENSE_PATTERN = "(ViPR_[a-zA-Z]+).+?VENDOR_STRING=" +
            "([a-zA-Z0-9=;_]*).*";
    public CoordinatorClientExt _coordinator;
    // Local URL format for internal nodes
    public static final String BASE_URL_FORMAT = "http://%1$s:%2$s";
    // statics for the Services keys for looking up host and port
    public static final String API_SVC_LOOKUP_KEY = "storageapisvc";
    public static final String SERVICE_LOOKUP_VERSION = "1";
    public static final String LICENSE_FEATRES_DELIM = "INCREMENT";
    public static final String LICENSE_EXPIRATION_DATE = "01/01/12006";
        
    public static String getModelId(LicenseType licenseType)
    {
        switch(licenseType) {
            case CONTROLLER:
                return VIPR_CONTROLLER;
            case OBJECT:
                return VIPR_OBJECT;
            case HDFS:
                return VIPR_HDFS;
            case OBJECTHDFS:
                return VIPR_OBJECTHDFS;
            case UNSTRUCTURED:
                return VIPR_UNSTRUCTURED;
            case CAS:
                return VIPR_CAS;
            case BLOCK:
                return VIPR_BLOCK;
            case COMMODITY:
                return VIPR_COMMODITY;
            case ECS:
                return VIPR_ECS;
            default:
                return "";
        }
    }
    
    public static LicenseType getLicenseType(String modelId)
    {
    	switch(modelId) {
            case VIPR_CONTROLLER:
                return LicenseType.CONTROLLER;
            case VIPR_OBJECT:
                return LicenseType.OBJECT;
            case VIPR_HDFS:
                return LicenseType.HDFS;
            case VIPR_UNSTRUCTURED:
                return LicenseType.UNSTRUCTURED;
            case VIPR_CAS:
                return LicenseType.CAS;
            case VIPR_BLOCK:
                return LicenseType.BLOCK;
            case VIPR_COMMODITY:
                return LicenseType.COMMODITY;
            case VIPR_ECS:
                return LicenseType.ECS;
            default:
                return null;
        }
    }
}
