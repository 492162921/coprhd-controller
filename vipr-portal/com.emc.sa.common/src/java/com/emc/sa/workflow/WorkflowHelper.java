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
package com.emc.sa.workflow;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.LoggerFactory;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAO;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveMapper;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAO;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAOs;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.Builder;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.ResourcePackage;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.ResourcePackage.ResourceBuilder;
import com.emc.sa.workflow.CustomServicesWorkflowPackage.WorkflowMetadata;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.model.uimodels.WFDirectory;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.ImmutableList;

/**
 * Helper class to perform CRUD operations on a workflow
 */
public final class WorkflowHelper {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(WorkflowHelper.class);
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WORKFLOWS_FOLDER = "workflows";
    private static final String OPERATIONS_FOLDER = "operations";
    private static final String RESOURCES_FOLDER = "resources";
    private static final String ROOT = "";
    private static final String METADATA_FILE = "workflow.md";
    private static final String CURRENT_VERSION = "1";
    private static final ImmutableList<String> SUPPORTED_VERSIONS = ImmutableList.<String>builder()
            .add(CURRENT_VERSION).build();
    
    private WorkflowHelper() {}
    
    /**
     * Create a workflow definition
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    public static CustomServicesWorkflow create(final CustomServicesWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {
        final CustomServicesWorkflow workflow = new CustomServicesWorkflow();
        
        workflow.setId(URIUtil.createId(CustomServicesWorkflow.class));
        workflow.setLabel(document.getName());
        workflow.setName(document.getName());
        workflow.setDescription(document.getDescription());
        workflow.setSteps(toStepsJson(document.getSteps()));
        workflow.setPrimitives(getPrimitives(document));
        return workflow;
    }
    
    /**
     * Update a workflow definition
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    public static CustomServicesWorkflow update(final CustomServicesWorkflow oeWorkflow, final CustomServicesWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {

        if(document.getDescription() != null) {
            oeWorkflow.setDescription(document.getDescription());
        }

        if(document.getName() != null) {
            oeWorkflow.setName(document.getName());
            oeWorkflow.setLabel(document.getName());
        }
        
        if( null != document.getSteps()  ) {
            oeWorkflow.setSteps(toStepsJson(document.getSteps()));
            oeWorkflow.setPrimitives(getPrimitives(document));
        }
        
        return oeWorkflow;
    }

    public static CustomServicesWorkflow updateState(final CustomServicesWorkflow oeWorkflow, final String state) {
        oeWorkflow.setState(state);
        return oeWorkflow;
    }
    
    public static CustomServicesWorkflowDocument toWorkflowDocument(final CustomServicesWorkflow workflow) throws JsonParseException, JsonMappingException, IOException {
        final CustomServicesWorkflowDocument document = new CustomServicesWorkflowDocument();
        document.setName(workflow.getName());
        document.setDescription(workflow.getDescription());
        document.setSteps(toDocumentSteps(workflow.getSteps()));
        
        return document;
    }
    
    public static CustomServicesWorkflowDocument toWorkflowDocument(final String workflow) throws JsonParseException, JsonMappingException, IOException {
        return MAPPER.readValue(workflow, CustomServicesWorkflowDocument.class);
    }
    
    public static String toWorkflowDocumentJson( CustomServicesWorkflow workflow) throws JsonGenerationException, JsonMappingException, JsonParseException, IOException {
        return MAPPER.writeValueAsString(toWorkflowDocument(workflow));
    }
    
    private static List<CustomServicesWorkflowDocument.Step> toDocumentSteps(final String steps) throws JsonParseException, JsonMappingException, IOException {
        return steps == null ? null :  MAPPER.readValue(steps, MAPPER.getTypeFactory().constructCollectionType(List.class, CustomServicesWorkflowDocument.Step.class));
    }
    
    private static String toStepsJson(final List<CustomServicesWorkflowDocument.Step> steps) throws JsonGenerationException, JsonMappingException, IOException {
        return MAPPER.writeValueAsString(steps);
    }
    
    private static StringSet getPrimitives(
            final CustomServicesWorkflowDocument document) {
        final StringSet primitives = new StringSet();
        for(final Step step : document.getSteps()) {
            final StepType stepType = (null == step.getType()) ? null : StepType.fromString(step.getType());
            if(null != stepType ) {
                switch(stepType) {
                case VIPR_REST:
                    break;
                default:
                    primitives.add(step.getOperation().toString());
                }
            }
        }
        return primitives;
    }

    /**
     * Import an archive given the tar.gz contents
     * 
     * @param archive the tar.gz contents of the workflow package
     * @param wfDirectory The directory to import the workflow to
     * @param client database client
     * @param daos DAO beans to access operations
     * @param resourceDAOs DAO beans to access resources
     * @return
     */
    public static CustomServicesWorkflow importWorkflow(final byte[] archive, 
            final WFDirectory wfDirectory, final ModelClient client, 
            final CustomServicesPrimitiveDAOs daos,
            final CustomServicesResourceDAOs resourceDAOs) {

        try (final TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new GZIPInputStream(new ByteArrayInputStream(
                        archive)))) {
            final CustomServicesWorkflowPackage.Builder builder = new CustomServicesWorkflowPackage.Builder();
            TarArchiveEntry entry = tarIn.getNextTarEntry();
            final Map<URI, ResourceBuilder> resourceMap = new HashMap<URI, ResourceBuilder>();
            while (entry != null) {
                if( !entry.isDirectory()) {
                    final Path path = getPath(entry);
                    final byte[] bytes = read(tarIn);
                    addEntry(builder, resourceMap, path, bytes); 
                    
                }
                entry = tarIn.getNextTarEntry();
            }
            
            for( final ResourceBuilder resourceBuilder : resourceMap.values()) {
                builder.addResource(resourceBuilder.build());
            }
            

            return importWorkflow(builder.build(), wfDirectory, client, daos, resourceDAOs);
            
        } catch (final IOException e) {
            log.error("Failed to import the archive: ", e);
            throw APIException.badRequests.workflowArchiveCannotBeImported(e.getMessage());
        }
    }

    private static CustomServicesWorkflow importWorkflow(final CustomServicesWorkflowPackage workflowPackage,
            final WFDirectory wfDirectory,
            final ModelClient client,
            final CustomServicesPrimitiveDAOs daos,
            final CustomServicesResourceDAOs resourceDAOs) throws JsonGenerationException, JsonMappingException, IOException {
       
        // TODO: This will only import new items.  If hte user wants to update an existing item they'll need to delete the 
        //       item and import it again.  We should support update of an item as will as import of new items.
        
        for( final Entry<URI, ResourcePackage> resource : workflowPackage.resources().entrySet()) {
            if( null == client.findById(URIUtil.getModelClass(resource.getKey()), resource.getKey())) {
                final CustomServicesPrimitiveResourceRestRep metadata = resource.getValue().metadata();
                final CustomServicesResourceDAO<?> dao = resourceDAOs.getByModel(URIUtil.getTypeName(metadata.getId()));
                if( null == dao ) {
                    throw new RuntimeException("Type not found for ID " + metadata.getId());
                }
                dao.importResource(metadata, resource.getValue().bytes());
                
            } else {
                log.info("Resource " + resource.getKey() + " previously imported");
            }
        }
        
        for( final Entry<URI, CustomServicesPrimitiveRestRep> operation : workflowPackage.operations().entrySet()) {
            if( null == client.findById(URIUtil.getModelClass(operation.getKey()), operation.getKey())) {
                final CustomServicesPrimitiveDAO<?> dao = daos.getByModel(URIUtil.getTypeName(operation.getKey()));
                if( null == dao ) {
                    throw new RuntimeException("Type not found for ID " + operation.getKey());
                }
                dao.importPrimitive(operation.getValue());
                if( null != wfDirectory.getId()) {
                    wfDirectory.addWorkflows(Collections.singleton(operation.getKey()));
                    client.save(wfDirectory);
                }
            }else {
                log.info("Primitive " + operation.getKey() + " previously imported");
            }
            
        }
        
        for(final  Entry<URI, CustomServicesWorkflowRestRep> workflow : workflowPackage.workflows().entrySet()) {
            if( null == client.customServicesWorkflows().findById(workflow.getKey())) {
                importWorkflow(workflow.getValue(), client, wfDirectory);
            } else {
                log.info("Workflow " + workflow.getKey() + " previously imported");
            }
        }
        return client.customServicesWorkflows().findById(workflowPackage.metadata().getId());
    }

    /**
     * @param client 
     * @param wfDirectory 
     * @param value
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    private static void importWorkflow(final CustomServicesWorkflowRestRep workflow, final ModelClient client, final WFDirectory wfDirectory) throws JsonGenerationException, JsonMappingException, IOException {
        final CustomServicesWorkflow dbWorkflow = new CustomServicesWorkflow();
        dbWorkflow.setId(workflow.getId());
        dbWorkflow.setLabel(workflow.getName());
        dbWorkflow.setName(workflow.getName());
        dbWorkflow.setInactive(false);
        dbWorkflow.setDescription(workflow.getDocument().getDescription());
        dbWorkflow.setSteps(toStepsJson(workflow.getDocument().getSteps()));
        dbWorkflow.setPrimitives(getPrimitives(workflow.getDocument()));
        client.save(dbWorkflow);
        if( null != wfDirectory.getId()) {
            wfDirectory.addWorkflows(Collections.singleton(workflow.getId()));
            client.save(wfDirectory);
        }
    }

    public static byte[] exportWorkflow(final URI id, 
            final ModelClient client, 
            final CustomServicesPrimitiveDAOs daos, 
            final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesWorkflowPackage workflowPackage = makeCustomServicesWorkflowPackage(id, client, daos, resourceDAOs);
        try(final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            makeArchive(out, workflowPackage);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param out
     * @param workflowPackage
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    private static void makeArchive(final ByteArrayOutputStream out, final CustomServicesWorkflowPackage workflowPackage) throws IOException {
        try(final TarArchiveOutputStream tarOut = new TarArchiveOutputStream(new GZIPOutputStream(new BufferedOutputStream(out)))) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            addArchiveEntry(tarOut, METADATA_FILE, new Date(System.currentTimeMillis()), MAPPER.writeValueAsBytes(workflowPackage.metadata()));
            
            for(  final Entry<URI, CustomServicesWorkflowRestRep> workflow : workflowPackage.workflows().entrySet()) {
                final String name = WORKFLOWS_FOLDER+"/"+workflow.getKey().toString();
                final byte[] content = MAPPER.writeValueAsBytes(workflow.getValue());
                final Date modTime = workflow.getValue().getCreationTime().getTime();
                addArchiveEntry(tarOut, name, modTime, content);
            }
            
            for( final Entry<URI, CustomServicesPrimitiveRestRep> operation : workflowPackage.operations().entrySet()) {
                final String name = OPERATIONS_FOLDER+"/"+operation.getKey().toString();
                final byte[] content = MAPPER.writeValueAsBytes(operation.getValue());
                final Date modTime = operation.getValue().getCreationTime().getTime();
                addArchiveEntry(tarOut, name, modTime, content);
            }

            for( final Entry<URI, ResourcePackage> resource : workflowPackage.resources().entrySet()) {
                final String name = RESOURCES_FOLDER+"/"+resource.getKey().toString()+".md";
                final String resourceFile = RESOURCES_FOLDER+"/"+resource.getKey().toString();
                final byte[] metadata = MAPPER.writeValueAsBytes(resource.getValue().metadata());
                final Date modTime = resource.getValue().metadata().getCreationTime().getTime();
                addArchiveEntry(tarOut, name, modTime, metadata);
                addArchiveEntry(tarOut, resourceFile, modTime, resource.getValue().bytes());
            }
            tarOut.finish();
        }
    }

    /**
     * @param id
     * @param client
     * @param resourceDAOs 
     * @return
     */
    private static CustomServicesWorkflowPackage makeCustomServicesWorkflowPackage(final URI id, final ModelClient client, final CustomServicesPrimitiveDAOs daos, final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesWorkflowPackage.Builder builder = new CustomServicesWorkflowPackage.Builder();
        builder.metadata(new WorkflowMetadata(id, CURRENT_VERSION));
        addWorkflow(builder, id, client, daos, resourceDAOs);
        return builder.build();
        
    }

    private static void addWorkflow(final Builder builder, final URI id, final ModelClient client, final CustomServicesPrimitiveDAOs daos, final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesWorkflow dbWorkflow = client.customServicesWorkflows().findById(id);
        if( null == dbWorkflow ) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        final CustomServicesWorkflowRestRep workflow = CustomServicesWorkflowMapper.map(dbWorkflow);
        builder.addWorkflow(workflow);
        for( final Step step : workflow.getDocument().getSteps()) {
            final String stepId = step.getId();
            if( !StepType.END.toString().equalsIgnoreCase(stepId) &&
                    !StepType.START.toString().equalsIgnoreCase(stepId) ) {
                final URI operation = step.getOperation();
                final String type = URIUtil.getTypeName(operation);
                if(type.equals(CustomServicesWorkflow.class.getSimpleName())) {
                    addWorkflow(builder, operation, client, daos, resourceDAOs);
                } else if( !type.equals(CustomServicesViPRPrimitive.class.getSimpleName())) {
                    addOperation(builder, operation, daos, resourceDAOs);
                }
            }
        }
    }

    private static void addOperation(final Builder builder, final URI id, final CustomServicesPrimitiveDAOs daos, final CustomServicesResourceDAOs resourceDAOs) {
        final CustomServicesPrimitiveDAO<?> dao = daos.getByModel(URIUtil.getTypeName(id));
        if( null == dao ) {
            throw new RuntimeException("Operation type for " + id + " not found");
        }
        
        final CustomServicesPrimitiveType primitive = dao.get(id);
        
        if( null == primitive ) {
            throw new RuntimeException("Operation with ID " + id + " not found");
        }
        
        if( null != primitive.resource() ) {
            addResource(builder, primitive.resource(), resourceDAOs);
        }
        
        builder.addOperation(CustomServicesPrimitiveMapper.map(primitive));
    }

    private static void addResource(Builder builder, NamedURI id, CustomServicesResourceDAOs resourceDAOs) {
        
        final CustomServicesResourceDAO<?> dao = resourceDAOs.getByModel(URIUtil.getTypeName(id.getURI()));
        
        if( null == dao ) {
            throw new RuntimeException("Resource type for " + id + " not found");
        }
        
        final CustomServicesPrimitiveResourceType resource = dao.getResource(id.getURI());
         
        if( null == resource ) {
            throw new RuntimeException("Resource " + id + " not found");
        }
        builder.addResource(new ResourcePackage(CustomServicesPrimitiveMapper.map(resource), resource.resource()));
        
        for( final NamedElement related : dao.listRelatedResources(id.getURI())) {
            addResource(builder, new NamedURI(related.getId(), related.getName()), resourceDAOs);
        }
    }

    private static void addArchiveEntry(final TarArchiveOutputStream tarOut, final String name, final Date modTime, final byte[] bytes) throws IOException {
        tarOut.putArchiveEntry(makeArchiveEntry(name, modTime, bytes));
        IOUtils.copy(new ByteArrayInputStream(bytes), tarOut);
        tarOut.closeArchiveEntry();
    }

    private static TarArchiveEntry makeArchiveEntry(final String name, final Date modTime, final byte[] bytes) throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        entry.setMode(0444);
        entry.setModTime(modTime);
        entry.setUserName("storageos");
        entry.setGroupName("storageos");
        return entry;
    }
    
    private static void addEntry(final CustomServicesWorkflowPackage.Builder builder, final Map<URI, ResourceBuilder> resourceMap,
            final Path path, final byte[] bytes) throws IOException, JsonParseException, JsonMappingException {
        final String parent = path.getParent() == null ? ROOT : path.getParent().getFileName().toString(); 
        switch(parent) {
            case ROOT:
                if( path.getFileName().toString().equals(METADATA_FILE)) {
                    addMetadata(builder, bytes);
                }
                return;
            case WORKFLOWS_FOLDER:
                addWorkflow(builder, bytes);
                return;
            case OPERATIONS_FOLDER:
                addOperation(builder, bytes);
                return;
            case RESOURCES_FOLDER:
                addResource(resourceMap, path, bytes);
                return;
            default:
                throw APIException.badRequests.workflowArchiveContentsInvalid(parent);
        }
    }

    private static void addResource(final Map<URI, ResourceBuilder> resourceMap, final Path path, final byte[] bytes) throws IOException,
            JsonParseException, JsonMappingException {
        final boolean isMetadata;
        final URI id;
        final String filename = path.getFileName().toString();
        if(filename.endsWith(".md")) {
            id = URI.create(filename.substring(0, filename.indexOf('.')));
            isMetadata = true;
        } else {
            id = URI.create(filename);
            isMetadata = false;
        }
        final ResourceBuilder resourceBuilder;
        if(!resourceMap.containsKey(id)) {
            resourceBuilder = new ResourceBuilder();
            resourceMap.put(id, resourceBuilder);
        } else {
            resourceBuilder = resourceMap.get(id);
        }
        
        if( isMetadata ) {
            resourceBuilder.metadata(MAPPER.readValue(bytes, CustomServicesPrimitiveResourceRestRep.class));
        } else {
            resourceBuilder.bytes(bytes);
        }
    }

    private static void addOperation(final CustomServicesWorkflowPackage.Builder builder, final byte[] bytes) throws IOException,
            JsonParseException, JsonMappingException {
        builder.addOperation(MAPPER.readValue(bytes, CustomServicesPrimitiveRestRep.class));
    }

    private static void addWorkflow(final CustomServicesWorkflowPackage.Builder builder, final byte[] bytes) throws IOException,
            JsonParseException, JsonMappingException {
        builder.addWorkflow(MAPPER.readValue(bytes, CustomServicesWorkflowRestRep.class));
    }

    private static void addMetadata(final CustomServicesWorkflowPackage.Builder builder, final byte[] bytes) throws IOException,
            JsonParseException, JsonMappingException {
        final WorkflowMetadata workflowMetadata = MAPPER.readValue(bytes, WorkflowMetadata.class);
        if( !SUPPORTED_VERSIONS.contains(workflowMetadata.getVersion())) {
            throw APIException.badRequests.workflowVersionNotSupported(workflowMetadata.getVersion(), SUPPORTED_VERSIONS);
        }
        builder.metadata(workflowMetadata);
    }
    
    private static byte[] read(final TarArchiveInputStream tarIn) throws IOException {
        try(final ByteArrayOutputStream out = new ByteArrayOutputStream() ) {
            IOUtils.copy(tarIn, out);
            return out.toByteArray();
        }
    }

    private static Path getPath(final TarArchiveEntry entry) {
        final Path path = FileSystems.getDefault().getPath(entry.getName());
        if(null == path) {
            throw APIException.badRequests.workflowArchiveCannotBeImported("Uknown file: " + entry.getName());
        }
        return path.normalize();
    }
}
