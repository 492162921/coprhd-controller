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
package com.emc.sa.catalog;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow.CustomServicesWorkflowStatus;
import org.springframework.util.CollectionUtils;


@Component
public class CustomServicesWorkflowManagerImpl implements
        CustomServicesWorkflowManager {
    @Autowired
    private ModelClient client;
 
    @Override
    public CustomServicesWorkflow getById(final URI id) {
        if( id == null ) {
            return null;
        }
        
        return client.customServicesWorkflows().findById(id);
    }

    @Override
    public List<CustomServicesWorkflow> getByName(final String name) {
        if( null == name) {
            return null;
        }
        
        return client.customServicesWorkflows().findByName(name);
    }
    
    @Override
    public List<NamedElement> list() {
        return client.customServicesWorkflows().findAllNames();
    }

    @Override
    public List<NamedElement> listByStatus(final CustomServicesWorkflowStatus status) {
        return client.customServicesWorkflows().findAllNamesByStatus(status);
    }

    @Override
    public Iterator<CustomServicesWorkflow> getSummaries(List<URI> ids) {
        return client.customServicesWorkflows().findSummaries(ids);
    }

    @Override
    public void save(final CustomServicesWorkflow workflow) {
        client.save(workflow);
    }
    
    @Override
    public void delete(final CustomServicesWorkflow workflow) {
        client.delete(workflow);
    }

    @Override
    public boolean hasCatalogServices(final String name) {
        final List<CatalogService> catalogServices = client.catalogServices().findByBaseService(name);
        if (!CollectionUtils.isEmpty(catalogServices)) {
            for(CatalogService cs: catalogServices){
                // Catalog services are not deleted if there are existing orders. Instead their Category ID will be set to DELETED
                // So return true only if there are catalog services that do not have this DELETED category ID.
                if(!cs.getCatalogCategoryId().getURI().toString().startsWith(CatalogCategory.DELETED_CATEGORY)){
                    return true;
                }
            }
        }
        return false;
    }
}
