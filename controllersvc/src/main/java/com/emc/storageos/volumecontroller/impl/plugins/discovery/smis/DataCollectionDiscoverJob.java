/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Job for Discover.
 */
public class DataCollectionDiscoverJob extends DataCollectionJob implements Serializable {

    private static final long serialVersionUID = -4345688816281981819L;
    private static final Logger logger = LoggerFactory
            .getLogger(DataCollectionDiscoverJob.class);
    private final DataCollectionTaskCompleter _completer;
    private String _namespace;

    public DataCollectionDiscoverJob(DiscoverTaskCompleter completer, String namespace) {
        this(completer, JobOrigin.USER_API, namespace);
    }

    DataCollectionDiscoverJob(DiscoverTaskCompleter completer, JobOrigin origin, String namespace) {
        super(origin);
        _completer = completer;
        _namespace = namespace;
    }

    @Override
    public DataCollectionTaskCompleter getCompleter() {
        return _completer;
    }

    @Override
    public void ready(DbClient dbClient) throws DeviceControllerException {
        _completer.ready(dbClient);
    }

    @Override
    public void error(DbClient dbClient, ServiceCoded serviceCoded) throws DeviceControllerException {
        _completer.error(dbClient, serviceCoded);
    }

    @Override
    public void schedule(DbClient dbClient) {
        _completer.schedule(dbClient);
    }

    @Override
    final public void setTaskError(DbClient dbClient, ServiceCoded code) {
        _completer.statusError(dbClient, code);
    }

    @Override
    final public void setTaskReady(DbClient dbClient, String message) {
        _completer.statusReady(dbClient, message);
    }

    @Override
    final public void updateTask(DbClient dbClient, String message) {
        _completer.statusPending(dbClient, message);
    }

    public String getType() {
        return _completer.getJobType();
    }

    public String systemString() {
        String sys = null;
        try {
            sys = getCompleter().getId().toString();
        } catch (Exception ex) {
            logger.error("Exception occurred while geting system id from completer", ex);
        }
        return sys;
    }

    public String getNamespace() {
        return _namespace;
    }

    public boolean isActiveJob(DbClient dbClient) {
        DataObject dbObject = dbClient.queryObject(_completer.getType(), _completer.getId());
        return (dbObject != null && !dbObject.getInactive()) ? true : false;
    }
    
    @Override
    public boolean matches(DataCollectionJob o) {
        if (o == null || !(o instanceof DataCollectionDiscoverJob)) {
            return false;
        }
        
        DataCollectionDiscoverJob other = (DataCollectionDiscoverJob) o;
        
        String thisResource = null;
        String otherResource = null;
        String thisType = null;
        String otherType = null;
        
        if (this._completer != null) {
            thisResource = this._completer.getId().toString();
            thisType = this._completer.getJobType();
        }
        
        if (other._completer != null) {
            otherResource = other._completer.getId().toString();
            otherType = other._completer.getJobType();
        }
        
        return StringUtils.equals(thisResource, otherResource) && StringUtils.equals(thisType, otherType);
    }
    
    @Override
    public String toString() {
        String resource = null;
        String type = null;
        
        if (this._completer != null) {
            resource = this._completer.getId().toString();
            type = this._completer.getJobType();
        }
        
        return String.format("%s job for resource %s", (type==null?"null":type), (resource==null?"null":resource));
    }

}
