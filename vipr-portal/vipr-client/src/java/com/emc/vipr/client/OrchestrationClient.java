/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.vipr.client;

import com.emc.storageos.model.orchestration.OrchestrationWorkflowList;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowRestRep;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowCreateParam;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowUpdateParam;
import com.emc.storageos.model.orchestration.PrimitiveList;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;

/**
 * Client for OE APIs - primitives, workflows
 */
public class OrchestrationClient {
    private final ViPRCatalogClient2 parent;
    private final RestClient client;

    public OrchestrationClient(final ViPRCatalogClient2 parent, final RestClient client) {
        this.parent = parent;
        this.client = client;
    }

    public ViPRCatalogClient2 getParent() {
        return parent;
    }

    public RestClient getClient() {
        return client;
    }

    public PrimitiveList getPrimitives() {
        UriBuilder builder = client.uriBuilder(PathConstants.OE_PRIMITIVES);
        return client.getURI(PrimitiveList.class, builder.build());
    }

    public OrchestrationWorkflowList getWorkflows() {
        UriBuilder builder = client.uriBuilder(PathConstants.OE_WORKFLOWS);
        return client.getURI(OrchestrationWorkflowList.class, builder.build());
    }

    public OrchestrationWorkflowRestRep getWorkflow(final URI id) {
        return client.get(OrchestrationWorkflowRestRep.class, PathConstants.OE_WORKFLOW, id);
    }

    public OrchestrationWorkflowRestRep createWorkflow(final OrchestrationWorkflowCreateParam param) {
        final UriBuilder builder = client.uriBuilder(PathConstants.OE_WORKFLOWS);
        return client.postURI(OrchestrationWorkflowRestRep.class,param, builder.build());
    }

    public OrchestrationWorkflowRestRep editWorkflow(final URI id, final OrchestrationWorkflowUpdateParam param) {
        return client.put(OrchestrationWorkflowRestRep.class,param,PathConstants.OE_WORKFLOW,id);
    }

    public void deleteWorkflow(final URI id) {
        client.post(String.class,PathConstants.OE_WORKFLOW_DELETE,id);
    }
}
