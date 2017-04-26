/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "diagutil_info")
public class DiagutilInfo {
    private String nodeId;
    private String startTimeStr;
    private DiagutilStatus status;
    private DiagutilStatusDesc desc;
    private String errcode;

    public DiagutilInfo() {

    }
    @XmlElement(name = "node_id")
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    @XmlElement(name = "start_time")
    public String getStartTimeStr() {
        return startTimeStr;
    }

    public void setStartTimeStr(String startTimeStr) {
        this.startTimeStr = startTimeStr;
    }

    @XmlElement(name = "status")
    public DiagutilStatus getStatus() {
        return status;
    }

    public void setStatus(DiagutilStatus status) {
        this.status = status;
    }

    @XmlElement(name = "description")
    public DiagutilStatusDesc getDesc() {
        return desc;
    }

    public void setDesc(DiagutilStatusDesc desc) {
        this.desc = desc;
    }

    @XmlElement(name = "error_code")
    public String getErrcode() {
        return errcode;
    }

    public void setErrcode(String errcode) {
        this.errcode = errcode;
    }

    public enum DiagutilStatus {
        PRECHECK_IN_PROGRESS,
        PRECHECK_SUCCESS,
        COLLECTING_IN_PROGRESS,
        COLLECTING_SUCCESS,
        UPLOADING_IN_PROGRESS,
        COMPLETE,
        PRECHECK_ERROR,
        COLLECTING_ERROR,
        UPLOADING_ERROR
    }

    public enum DiagutilStatusDesc {
        PRECHECK_IN_PROGRESS,
        COLLECTING_ZK,
        COLLECTING_DB,
        COLLECTING_LOGS,
        COLLECTING_PROPERTIES,
        COLLECTING_HEALTH,
        COLLECTING_BACKUP,
        COLLECT_COMPLETE,
        UPLOADING_INPROGRESS,
        UPLOAD_COMPLETE,
        DISK_FULL,
        COLLECTING_ZK_FAILURE,
        COLLECTING_DB_FAILURE,
        COLLECTING_PROPERTIES_FAILURE,
        COLLECTING_HEALTH_FAILURE,
        COLLECTING_BACKUP_FAILURE,
        UPLOAD_FAILURE
    }




}
