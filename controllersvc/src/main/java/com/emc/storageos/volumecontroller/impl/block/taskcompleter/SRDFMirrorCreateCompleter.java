/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import static java.util.Arrays.asList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.impl.smis.SmisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class SRDFMirrorCreateCompleter extends SRDFTaskCompleter {
    private static final Logger log = LoggerFactory.getLogger(SRDFMirrorCreateCompleter.class);

    private String sourceRepGroup;

    private String targetRepGroup;

    private URI sourceCGUri;

    private URI vpoolChangeURI;

    public SRDFMirrorCreateCompleter(final URI sourceURI, final URI targetUri, final URI vPoolChangeUri, final String opId) {
        super(asList(sourceURI, targetUri), opId);
        vpoolChangeURI = vPoolChangeUri;
    }

    public SRDFMirrorCreateCompleter(List<URI> volumeURIs, final URI vPoolChangeUri, String opId) {
        super(volumeURIs, opId);
        vpoolChangeURI = vPoolChangeUri;
    }

    public void setCGName(final String sourceGroupName, final String targetGroupName,
            final URI sourceCGUri) {
        this.sourceRepGroup = sourceGroupName;
        this.targetRepGroup = targetGroupName;
        this.sourceCGUri = sourceCGUri;
    }
    
    public URI getVirtualPoolChangeURI() {
      return vpoolChangeURI;
    }

    @Override
    public void complete(final DbClient dbClient, final Operation.Status status,
            final ServiceCoded coded) throws DeviceControllerException {
        log.info("Completing with status: {}", status);
        setDbClient(dbClient);
        Volume target = null;
        Volume source = null;
        try {
            switch (status) {

                case ready:
                    target = getTargetVolume();
                    // updating source volume with changed VPool
                    source = dbClient.queryObject(Volume.class, target.getSrdfParent().getURI());
                    if (null != vpoolChangeURI) {
                        source.setVirtualPool(vpoolChangeURI);
                        dbClient.persistObject(source);
                    }
                    // Pin the target System with the source CG, which helps to identify this system is
                    // a target R2 for CG.
                    if (null != sourceCGUri) {
                        URI targetSystemUri = target.getStorageController();
                        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                                targetSystemUri);
                        if (targetSystem.getTargetCgs() == null) {
                            targetSystem.setTargetCgs(new StringSet());
                        }
                        targetSystem.getTargetCgs().add(sourceCGUri.toString());
                        dbClient.persistObject(targetSystem);
                    }

                    String copyMode = target.getSrdfCopyMode();
                    RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                            target.getSrdfGroup());
                    log.info("Setting copyMode {} to RAGroup {}", copyMode, group.getId());
                    group.setSupportedCopyMode(copyMode);
                    group.setSourceReplicationGroupName(sourceRepGroup);
                    group.setTargetReplicationGroupName(targetRepGroup);
                    if (null == group.getVolumes()) {
                        group.setVolumes(new StringSet());
                    }

                    group.getVolumes().addAll(getVolumeIds());
                    dbClient.persistObject(group);
                    break;

                default:
                    log.info("Unable to handle status: {}", status);
            }
            recordSRDFOperation(dbClient, OperationTypeEnum.CREATE_SRDF_LINK, status, getSourceVolume().getId().toString(),
                    getTargetVolume().getId().toString());
        } catch (Exception e) {
            log.info("Failed to update status for task {}", getOpId(), e);
        } finally {
            super.complete(dbClient, status, coded);
            if (status.equals(Operation.Status.ready)) {
                log.info("Process remote replication pairs for srdf link create. Status: {}", status);
                try {
                    // at this point we are done with all db updates for SRDF volumes, now update remote replication pairs
                    super.updateRemoteReplicationPairs();
                } catch (Exception ex) {
                    ServiceError error = SmisException.errors.jobFailed(ex.getMessage());
                    this.error(dbClient, error);
                }
            }
        }
    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.IN_SYNC;
    }
}
