/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.WorkflowMapper.map;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Workflow;
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.model.workflow.StepList;
import com.emc.storageos.model.workflow.WorkflowList;
import com.emc.storageos.model.workflow.WorkflowRestRep;
import com.emc.storageos.model.workflow.WorkflowStepRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

/**
 * API interface for a Workflow and WorkflowStep.
 * This interface is read-only and returns historical information about
 * Workflow execution.
 * 
 * @author Watson
 */
@Path("/vdc/workflows")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
        Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.TENANT_ADMIN })
public class WorkflowService extends ResourceService {
    protected Workflow queryResource(URI id) {
        ArgValidator.checkUri(id);
        Workflow workflow = _dbClient.queryObject(Workflow.class, id);
        ArgValidator.checkEntityNotNull(workflow, id, isIdEmbeddedInURL(id));

        return workflow;
    }

    /**
     * Returns a list of all Workflows.
     * 
     * @brief List all workflows
     * @return WorkflowList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowList getWorkflows() {
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        WorkflowList list = new WorkflowList();
        for (URI workflowId : workflowIds) {
            Workflow workflow = _dbClient.queryObject(Workflow.class, workflowId);
            if (workflow == null) {
                continue;
            }
            list.getWorkflows().add(map(workflow));
            workflow = null;
        }
        return list;
    }

    /**
     * Returns the active workflows.
     * 
     * @brief List active workflows
     */
    @GET
    @Path("/active")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowList getActiveWorkflows() {
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        List<Workflow> workflows = _dbClient.queryObject(Workflow.class, workflowIds);
        WorkflowList list = new WorkflowList();
        for (Workflow workflow : workflows) {
            if (workflow.getCompleted() == false) {
                list.getWorkflows().add(map(workflow));
            }
        }
        return list;
    }

    /**
     * Returns the completed workflows.
     * 
     * @brief List completed workflows
     */
    @GET
    @Path("/completed")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowList getCompletedWorkflows() {
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        List<Workflow> workflows = _dbClient.queryObject(Workflow.class, workflowIds);
        WorkflowList list = new WorkflowList();
        for (Workflow workflow : workflows) {
            if (workflow.getCompleted() == true) {
                list.getWorkflows().add(map(workflow));
            }
        }
        return list;
    }

    /**
     * Returns workflows created in the last specified number of minutes.
     * 
     * @brief List workflows created in specified time period
     */
    @GET
    @Path("/recent")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowList getRecentWorkflows(@QueryParam("min") String minutes) {
        if (minutes == null) {
            minutes = "10";
        }
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        List<Workflow> workflows = _dbClient.queryObject(Workflow.class, workflowIds);
        Long timeDiff = new Long(minutes) * 1000 * 60;
        Long currentTime = System.currentTimeMillis();
        WorkflowList list = new WorkflowList();
        for (Workflow workflow : workflows) {
            // If created in the last n minutes
            if ((currentTime - workflow.getCreationTime().getTimeInMillis()) < timeDiff) {
                list.getWorkflows().add(map(workflow));
            }
        }
        return list;
    }

    /**
     * Returns information about the specified workflow.
     * 
     * @param id the URN of a ViPR workflow
     * @brief Show workflow
     * @return
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowRestRep getWorkflow(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Workflow.class, "id");
        Workflow workflow = queryResource(id);
        return map(workflow);
    }

    /**
     * Gets a list of all the steps in a particular workflow.
     * 
     * @param id the URN of a ViPR workflow
     * @brief List workflow steps
     * @return
     */
    @GET
    @Path("/{id}/steps")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public StepList getStepList(@PathParam("id") URI id) {
        StepList list = new StepList();
        URIQueryResultList stepURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getWorkflowWorkflowStepConstraint(id),
                stepURIs);
        Iterator<URI> iter = stepURIs.iterator();
        while (iter.hasNext()) {
            URI workflowStepURI = iter.next();
            WorkflowStep step = _dbClient
                    .queryObject(WorkflowStep.class, workflowStepURI);
            list.getSteps().add(map(step, getChildWorkflows(step)));
        }
        return list;
    }

    /**
     * Returns a single WorkflowStep.
     * 
     * @param stepId
     * @brief Show workflow step
     * @return
     */
    @GET
    @Path("/steps/{stepid}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowStepRestRep getStep(@PathParam("stepid") URI stepId) {
        ArgValidator.checkFieldUriType(stepId, WorkflowStep.class, "stepid");
        WorkflowStep step = _dbClient.queryObject(WorkflowStep.class, stepId);
        ArgValidator.checkEntityNotNull(step, stepId, isIdEmbeddedInURL(stepId));
        return map(step, getChildWorkflows(step));
    }

    private List<URI> getChildWorkflows(WorkflowStep step) {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getWorkflowByOrchTaskId(step.getStepId()), result);
        List<URI> childWorkflows = new ArrayList<URI>();
        while (result.iterator().hasNext()) {
            childWorkflows.add(result.iterator().next());
        }
        return childWorkflows;
    }
}
