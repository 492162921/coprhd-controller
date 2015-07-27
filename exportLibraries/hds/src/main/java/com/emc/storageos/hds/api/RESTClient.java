/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.hds.api;

import java.net.URI;

import com.emc.storageos.hds.HDSConstants;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.binary.Base64;

/**
 * Generic REST client over HTTP.
 */
public class RESTClient {

    // A reference to the Jersey Apache HTTP client.
    private Client _client;

    // The user to be authenticated for requests made by the client.
    private String _username;

    // The password for user authentication.
    private String _password;

    /**
     * Constructor
     * 
     * @param client A reference to a Jersey Apache HTTP client.
     * @param username The user to be authenticated.
     * @param password The user password for authentication.
     */
    RESTClient(Client client, String username, String password) {
        _client = client;
        _username = username;
        _password = password;
    }

    /**
     * GET the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse get(URI uri) {
        return setResourceHeaders(_client.resource(uri)).get(ClientResponse.class);
    }

    /**
     * PUT to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse put(URI uri) {
        return setResourceHeaders(_client.resource(uri)).put(ClientResponse.class);
    }

    /**
     * PUT to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The PUT data.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse put(URI uri, String body) {
        return setResourceHeaders(_client.resource(uri)).type(MediaType.TEXT_XML)
            .put(ClientResponse.class, body);
    }

    /**
     * POST to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The POST data.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse post(URI uri, String body) {
        return setResourceHeaders(_client.resource(uri)).type(MediaType.TEXT_XML)
            .post(ClientResponse.class, body);
    }

    /**
     * Close the client
     */
    void close() {
        _client.destroy();
    }

    /**
     * Sets required headers into the passed WebResource.
     * 
     * @param resource The resource to which headers are added.
     */
    Builder setResourceHeaders(WebResource resource) {
        StringBuffer credentials = new StringBuffer(_username).append(HDSConstants.COLON).append(_password);
        Base64 base64 = new Base64();
        String encodedStr = base64.encodeToString(credentials.toString().getBytes());
        return resource.header("Authorization", "Basic " + encodedStr);
    }
}
