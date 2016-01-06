/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "volume_group")
public class VolumeGroupRestRep extends DataObjectRestRep {
    private String description;
    private Set<String> roles;
    private RelatedResourceRep parent;
    private Set<String> replicationGroupNames;

    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElementWrapper(name = "roles")
    /**
     * Roles of the volume group
     * 
     * @valid none
     */
    @XmlElement(name = "role")
    public Set<String> getRoles() {
        if (roles == null) {
            roles = new HashSet<String>();
        }
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    /**
     * Related parent volume group
     * 
     * @valid none
     */
    @XmlElement(name = "parent")
    @JsonProperty("parent")
    public RelatedResourceRep getParent() {
        return parent;
    }

    public void setParent(RelatedResourceRep parent) {
        this.parent = parent;
    }

    /**
     * @return the replicationGroupNames
     */
    @XmlElementWrapper(name = "replication-group-names")
    @XmlElement(name = "replication-group-name")
    public Set<String> getReplicationGroupNames() {
        if (replicationGroupNames == null) {
            replicationGroupNames = new HashSet<String>();
        }
        return replicationGroupNames;
    }

    /**
     * @param replicationGroupNames the replicationGroupNames to set
     */
    public void setReplicationGroupNames(Set<String> replicationGroupNames) {
        this.replicationGroupNames = replicationGroupNames;
    }
}
