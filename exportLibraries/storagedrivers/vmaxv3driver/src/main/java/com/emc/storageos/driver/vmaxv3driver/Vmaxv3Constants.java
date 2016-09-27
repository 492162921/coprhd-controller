/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver;

/**
 * This class defines all the constants of this project.
 *
 * Created by gang on 6/21/16.
 */
public class Vmaxv3Constants {
    public static final String DRIVER_NAME = "VMAXV3";

    // Define the REST API paths.
    public static final String RA_COMMON_ITERATOR_PAGE =
        "/univmax/restapi/common/Iterator/%1$s/page?from=%2$d&to=%3$d";
    public static final String RA_SLOPROVISIONING_SYMMETRIX =
        "/univmax/restapi/sloprovisioning/symmetrix";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_ID =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_SRP =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/srp";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_SRP_ID =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/srp/%2$s";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_DIRECTOR =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/director";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_DIRECTOR_PORT =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/director/%2$s/port";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_DIRECTOR_PORT_ID =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/director/%2$s/port/%3$s";
    public static final String RA_SYSTEM_VERSION =
        "/univmax/restapi/system/version";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_STORAGE_GROUP =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/storagegroup";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_STORAGE_GROUP_ID =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/storagegroup/%2$s";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_STORAGE_GROUP_LIST_BY_NAME =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/storagegroup?storageGroupId=%%3Clike%%3E%2$s";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_VOLUME_LIST_BY_STORAGE_GROUP =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/volume?storageGroupId=%2$s";
    public static final String RA_SLOPROVISIONING_SYMMETRIX_VOLUME =
        "/univmax/restapi/sloprovisioning/symmetrix/%1$s/volume/%2$s";
}
