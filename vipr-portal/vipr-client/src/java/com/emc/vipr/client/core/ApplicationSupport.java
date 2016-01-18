/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_CREATE_APP_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_DELETE_APP_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_GET_HOSTS_APP_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_GET_VOLUMES_APP_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_UPDATE_APP_URL;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.application.VolumeGroupCreateParam;
import com.emc.storageos.model.application.VolumeGroupList;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.host.HostList;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class ApplicationSupport extends AbstractResources<VolumeGroupRestRep> {
    protected final RestClient client;

    public ApplicationSupport(RestClient client) {
        super(client, VolumeGroupRestRep.class, PathConstants.APP_SUPPORT_CREATE_APP_URL);
        this.client = client;
    }

    /**
     * Creates an application.
     * <p>
     * API Call: POST /volume-groups/block
     * 
     * @return The new state of the cluster
     */
    public VolumeGroupRestRep createApplication(VolumeGroupCreateParam input) {
        return client.post(VolumeGroupRestRep.class, input, APP_SUPPORT_CREATE_APP_URL);
    }

    /**
     * Get List of applications
     * API call: GET /volume-groups/block
     * 
     * @return List of applications
     */

    public VolumeGroupList getApplications() {
        return client.get(VolumeGroupList.class, APP_SUPPORT_CREATE_APP_URL, "");
    }

    public List<VolumeGroupRestRep> getApplications(ResourceFilter<VolumeGroupRestRep> filter) {
        VolumeGroupList groups = getApplications();
        return this.getByRefs(groups.getVolumeGroupss(), filter);
    }

    /**
     * Deletes an application
     * API Call: POST /volume-groups/block/{id}/deactivate
     * 
     */
    public void deleteApplication(URI id) {
        client.post(String.class, APP_SUPPORT_DELETE_APP_URL, id);
    }
    
    /**
     * Update an application
     * API call: PUT /volume-groups/block/{id}
     * 
     */
    public TaskList updateApplication(URI id, VolumeGroupUpdateParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_UPDATE_APP_URL);
        return client.putURI(TaskList.class, input, uriBuilder.build(id));
    }
    
    /**
     * Get application based on ID
     * 
     */
    public VolumeGroupRestRep getApplication(URI id) {
        return client.get(VolumeGroupRestRep.class, APP_SUPPORT_UPDATE_APP_URL, id);
    }

    public List<NamedRelatedResourceRep> getVolumes(URI id) {
        NamedVolumesList response = client.get(NamedVolumesList.class, APP_SUPPORT_GET_VOLUMES_APP_URL, id);
        return response.getVolumes();
    }

    public List<NamedRelatedResourceRep> getHosts(URI id) {
        HostList response = client.get(HostList.class, APP_SUPPORT_GET_HOSTS_APP_URL, id);
        return response.getHosts();
    }
}