/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.emc.storageos.model.RelatedResourceRep;

/**
 * Class encapsulates the data returned in response to a request
 * for a BlockSnapshotSession instance.
 */
public class BlockSnapshotSessionRestRep extends BlockObjectRestRep {

    // Related resource representation for the snapshot session source object.
    private RelatedResourceRep parent;

    // Related resource representation for the snapshot session source project.
    private RelatedResourceRep project;

    // Related resource representations for the BlockSnapshot instances
    // representing the targets linked to the snapshot session.
    private List<RelatedResourceRep> linkedTargets;

    // The session label.
    private String sessionLabel;

    /**
     * URI and reference link to the snapshot session source.
     * 
     * @valid none
     */
    @XmlElement
    public RelatedResourceRep getParent() {
        return parent;
    }

    public void setParent(RelatedResourceRep parent) {
        this.parent = parent;
    }

    /**
     * URI and reference link of the project to which the snapshot belongs.
     * 
     * @valid none
     */
    @XmlElement
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * List of target volumes, i.e., BlockSnapshot instances, linked to the
     * block snapshot session.
     * 
     * @valid none
     */
    @XmlElementWrapper(name = "linked_targets")
    @XmlElement(name = "linked_target")
    public List<RelatedResourceRep> getLinkedTarget() {
        if (linkedTargets == null) {
            linkedTargets = new ArrayList<RelatedResourceRep>();
        }
        return linkedTargets;
    }

    public void setLinkedTargets(List<RelatedResourceRep> linkedTargets) {
        this.linkedTargets = linkedTargets;
    }

    /**
     * User specified session label.
     * 
     * @valid none
     */
    @XmlElement(name = "session_label")
    public String getSessionLabel() {
        return sessionLabel;
    }

    public void setSessionLabel(String sessionLabel) {
        this.sessionLabel = sessionLabel;
    }
}
