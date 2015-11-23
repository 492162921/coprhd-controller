/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.application;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Application creation parameters
 */
@XmlRootElement(name = "application_create")
public class ApplicationCreateParam {
    private String name;
    private String description;
    private Set<String> roles;

    public ApplicationCreateParam() {
    }

    public ApplicationCreateParam(String name, String description, Set<String> roles) {
        this.name = name;
        this.description = description;
        this.roles = roles;
    }

    /**
     * Application unique name
     * 
     * @valid minimum of 2 characters
     * @valid maximum of 128 characters
     */
    @XmlElement(required = true)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Application description
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElementWrapper(name = "roles", required = true)
    /**
     * The set of supported roles for the application.
     * 
     * @valid COPY
     * @valid DR
     */
    @XmlElement(name = "role", required = true)
    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

}
