/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

/*
 * Copyright (c) 2014. EMC Corporation All Rights Reserved This software contains the intellectual
 * property of EMC Corporation or is licensed to EMC Corporation from third parties. Use of this
 * software and the intellectual property contained therein is expressly limited to the terms and
 * conditions of the License Agreement under which it is provided by or on behalf of EMC.
 */

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.List;

public class SRDFLinkStopCompleter extends SRDFTaskCompleter {

    private static final long serialVersionUID = -8405901055468081447L;
    private static final Logger _log = LoggerFactory.getLogger(SRDFLinkStopCompleter.class);
    private Collection<Volume> srcVolumes;
    private Collection<Volume> tgtVolumes;

    public SRDFLinkStopCompleter(List<URI> ids, String opId) {
        super(ids, opId);
    }
    
    public void setVolumes(Collection<Volume> srcVolumes, Collection<Volume> targetVolumes) {
        this.srcVolumes = srcVolumes;
        this.tgtVolumes = targetVolumes;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        try {
            setDbClient(dbClient);
          
            switch (status) {

            case ready:

                if (null != srcVolumes && null != tgtVolumes && !srcVolumes.isEmpty() && !tgtVolumes.isEmpty()) {
                    for (Volume sourceVol : srcVolumes) {
                        sourceVol.setPersonality(NullColumnValueGetter.getNullStr());
                        sourceVol.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                        if (null != sourceVol.getSrdfTargets()) {
                            sourceVol.getSrdfTargets().clear();
                        }
                        sourceVol.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                        dbClient.persistObject(sourceVol);
                    }

                    for (Volume target : tgtVolumes) {
                        target.setPersonality(NullColumnValueGetter.getNullStr());
                        target.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                        target.setSrdfParent(new NamedURI(NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullStr()));
                        target.setSrdfCopyMode(NullColumnValueGetter.getNullStr());
                        target.setSrdfGroup(NullColumnValueGetter.getNullURI());
                        target.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                        dbClient.updateAndReindexObject(target);
                    }

                    Volume target = tgtVolumes.iterator().next();
                    Volume source = srcVolumes.iterator().next();
                    _log.info("SRDF Devices source {} and target {} converted to non srdf devices", source.getId(),
                            target.getId());
                    recordSRDFOperation(dbClient, OperationTypeEnum.STOP_SRDF_LINK, status, source.getId().toString(), target
                            .getId().toString());
                }
            default:
                _log.info("Unable to handle SRDF Link Stop Operational status: {}", status);
            }

        } catch (Exception e) {
            _log.error("Failed updating status. SRDFMirrorStop {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.DETACHED;
    }

}
