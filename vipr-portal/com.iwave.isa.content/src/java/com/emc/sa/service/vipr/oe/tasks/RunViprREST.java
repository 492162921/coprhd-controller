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

package com.emc.sa.service.vipr.oe.tasks;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.gson.ViprOperation;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants;
import com.emc.sa.service.vipr.oe.OrchestrationUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.primitives.ViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

/**
 * This provides ability to run ViPR REST APIs
 */
public class RunViprREST extends ViPRExecutionTask<OrchestrationTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunViprREST.class);

    private final Map<String, List<String>> input;
    private final RestClient client;
    private final ViPRPrimitive primitive;

    public RunViprREST(final ViPRPrimitive primitive, final RestClient client, final Map<String, List<String>> input) {
        this.input = input;
        this.client = client;
        this.primitive = primitive;
    }

    @Override
    public OrchestrationTaskResult executeTask() throws Exception {

        final String requestBody;

        final String templatePath = primitive.path();
        final String body = primitive.body();
        final String method = primitive.method();

        if (OrchestrationServiceConstants.BODY_REST_METHOD.contains(method) && !body.isEmpty()) {
            requestBody = makePostBody(body);
        } else {
            requestBody = "";
        }

        String path = makePath(templatePath);

        ExecutionUtils.currentContext().logInfo("runViprREST.startInfo", primitive.getFriendlyName());

        OrchestrationTaskResult result = makeRestCall(path, requestBody, method);

        ExecutionUtils.currentContext().logInfo("runViprREST.doneInfo", primitive.getFriendlyName());

        return result;
    }

    private Map<URI, String> waitForTask(final String result) throws InternalServerErrorException
    {
        final ViprOperation res = OrchestrationUtils.parseViprTasks(result);
        if (res == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceNoTaskFound("no task found");
        }

        try {
            return OrchestrationUtils.waitForTasks(res.getTaskIds(), getClient());
        } catch (final InterruptedException e) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Task Timed Out");
        } catch (final URISyntaxException e) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Failed to parse REST response");
        }
    }

    private OrchestrationTaskResult makeRestCall(final String path, final Object requestBody, final String method) throws Exception {

        final ClientResponse response;
        OrchestrationServiceConstants.restMethods restmethod = OrchestrationServiceConstants.restMethods.valueOf(method);

        switch(restmethod) {
            case GET:
                response = client.get(ClientResponse.class, path);
                break;
            case PUT: 
                response = client.put(ClientResponse.class, requestBody, path);
                break;
            case POST:
                response = client.post(ClientResponse.class, requestBody, path);
                break;
            case DELETE:
                response = client.delete(ClientResponse.class, path);
                break;
            default:
                throw InternalServerErrorException.internalServerErrors.
                        customServiceExecutionFailed("Invalid REST method type" + method);
        }

        if (response == null) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("REST Execution Failed" + response);
        }

        logger.info("Status of ViPR REST Operation:{} is :{}", primitive.getName(), response.getStatus());

        String responseString = null;
        try {
            responseString = IOUtils.toString(response.getEntityInputStream(), "UTF-8");

            final Map<URI, String> taskState = waitForTask(responseString);
            return new OrchestrationTaskResult(responseString, responseString, response.getStatus(), taskState);
        } catch (final InternalServerErrorException e) {
            if (e.getServiceCode().getCode() == ServiceCode.CUSTOM_SERVICE_NOTASK.getCode()) {
                logger.info("service code is:{}", ServiceCode.CUSTOM_SERVICE_NOTASK.getCode());
                return new OrchestrationTaskResult(responseString, responseString, response.getStatus(), null);
            }
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Failed to Execute REST request");
        } catch (final IOException e) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Unable to get response" + response);
        }
    }


    /**
     * Example uri: "/block/volumes/{id}/findname/{name}";
     * @param templatePath
     * @return
     */
    private String makePath(String templatePath) {
        final UriTemplate template = new UriTemplate(templatePath);
        final List<String> pathParameters = template.getVariableNames();
        final Map<String, Object> pathParameterMap = new HashMap<String, Object>();
        
        for(final String key : pathParameters) {
            List<String> value = input.get(key);
            if(null == value) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Unfulfilled path parameter: " + key);
            }
            pathParameterMap.put(key, value.get(0));
        }
        
        final String path = template.expand(pathParameterMap).getPath(); 

        logger.info("URI string is: {}", path);

        return path;
    }


    /**
     * POST body format:
     *  "{\n" +
     "  \"consistency_group\": \"$consistency_group\",\n" +
     "  \"count\": \"$count\",\n" +
     "  \"name\": \"$name\",\n" +
     "  \"project\": \"$project\",\n" +
     "  \"size\": \"$size\",\n" +
     "  \"varray\": \"$varray\",\n" +
     "  \"vpool\": \"$vpool\"\n" +
     "}";
     * @param body
     * @return
     */
    private String makePostBody(String body) {

        Matcher m = Pattern.compile("\\$(\\w+)").matcher(body);

        while (m.find()) {
            String pat = m.group(1);
            String newpat = "$" + pat;
            body = body.replace(newpat, "\"" +input.get(pat).get(0)+"\"");
        }

        return body;
    }
}

