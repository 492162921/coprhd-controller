/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cinder;

public interface CinderConstants {

    String HTTPS_URL = "https://";
    String HTTP_URL = "http://";
    String COLON = ":";
    String ATTACH_RESPONSE_FC_TYPE = "fibre_channel";
    String ATTACH_RESPONSE_ISCSI_TYPE = "iscsi";
    String HYPHEN = "-";
    char CHAR_HYPHEN = '-';
    String DEFAULT = "DEFAULT";

    public static String CINDER_SSH_PORT = "22";
    public static String CINDER_REST_PORT = "8776";
    public static String AUTH_TOKEN_HEADER = "X-Auth-Token";
    public static String REST_API_VERSION_2 = "/v2";
    public static String DEFAULT_API_VERSION = REST_API_VERSION_2; // By default using v2

    public final static String KEY_CINDER_HOST_NAME = "CINDER_HOST_NAME";
    public final static String KEY_CINDER_REST_PASS_WORD = "REST_PASSWORD";
    public final static String KEY_CINDER_REST_USER = "REST_USERNAME";
    public final static String KEY_CINDER_REST_URI_BASE = "REST_URI_BASE";
    public final static String KEY_CINDER_TENANT_NAME = "TENANT_NAME";
    public final static String KEY_CINDER_REST_TOKEN = "REST_TOKEN";
    public final static String KEY_CINDER_TENANT_ID = "TENANT_ID";

    public final static String CINDER_PORT_GROUP = "Cinder-PortGroup";

    public final static String PREFIX_ITL = "ITL-";
    
    public final static long BYTES_TO_GB = 1024*1024*1024;
    
    /**
     * URIs for volume types
     */
    public static String URI_LIST_VOLUME_TYPES = DEFAULT_API_VERSION + "/%1$s/types";

    /**
     * URIs for volume operations
     */
    public static String URI_LIST_VOLUMES = DEFAULT_API_VERSION + "/%1$s/volumes";
    public static String URI_CREATE_VOLUME = URI_LIST_VOLUMES;
    public static String URI_DELETE_VOLUME = DEFAULT_API_VERSION + "/%1$s/volumes/%2$s";
    public static String URI_UPDATE_VOLUME = DEFAULT_API_VERSION + "/%1$s/volumes/%2$s?display_description=%3$s&display_name=%4$s}";

    /**
     * URIs for volume export operations
     */
    public static String URI_VOLUME_ACTION = DEFAULT_API_VERSION + "/%1$s/volumes/%2$s/action";
    public static String URI_DETACH_VOLUME = URI_VOLUME_ACTION;

    /**
     * URIs for snapshot operations
     */
    public static String URI_LIST_SNAPSHOTS = DEFAULT_API_VERSION + "/%1$s/snapshots";
    public static String URI_CREATE_SNAPSHOT = URI_LIST_SNAPSHOTS;
    public static String URI_DELETE_SNAPSHOT = DEFAULT_API_VERSION + "/%1$s/snapshots/%2$s";
    public static String URI_UPDATE_SNAPSHOT = DEFAULT_API_VERSION + "/%1$s/snapshots/%2$s​?display_description=%3$s&​display_name=%4$s";

    /*
     * Enum types for the status check of the components ( volume/snapshot )
     * under creation/modification.
     */
    public static enum ComponentStatus
    {

        CREATING("Creating"),
        AVAILABLE("Available"),
        ERROR("Error"),
        ATTACHING("Attaching"),
        IN_USE("In-Use"),
        EXTENDING("Extending"),
        DETACHING("Detatching"),
        DELETED("Deleted"),
        DELETING("Deleting"),
        ERROR_DELETING("Error_Deleting"),
        ERROR_EXTENDING("Error_Extending");

        public String status = "";

        ComponentStatus(String status)
        {
            this.status = status;
        }

        public String getStatus()
        {
            return this.status;
        }
    }

    /**
     * Enum types for the types of the components ( volume/snapshot )
     * 
     */

    public static enum ComponentType {
        volume,
        snapshot;
    }

}
