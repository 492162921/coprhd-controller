/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByAssociatedId;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Task completer for update application volumes
 *
 */
public class ApplicationTaskCompleter extends TaskCompleter{

    private static final long serialVersionUID = -9188670003331949130L;
    private static final Logger log = LoggerFactory.getLogger(ApplicationTaskCompleter.class);
    private List<URI> addVolumes;
    private List<URI> removeVolumes;
    private List<URI> consistencyGroups;
    
    public ApplicationTaskCompleter(URI volumeGroupId, List<URI> addVolumes, List<URI>removeVols, List<URI> consistencyGroups, String opId) {
        super(VolumeGroup.class, volumeGroupId, opId);
        this.addVolumes = addVolumes;
        this.removeVolumes = removeVols;
        this.consistencyGroups = consistencyGroups;
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        log.info("START ApplicationCompleter complete");
        super.setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
        for (URI voluri : addVolumes) {
            Volume volume = getVolume(voluri, dbClient);
            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, Volume.class, volume.getId(), coded);
                    break;
                default:
                    setReadyOnDataObject(dbClient, Volume.class, volume.getId());
                    addApplicationToVolume(volume, dbClient);
            }
        }
        for (URI voluri : removeVolumes) {
            Volume volume = getVolume(voluri, dbClient);
           switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, Volume.class, volume.getId(), coded);
                    break;
                default:
                    setReadyOnDataObject(dbClient, Volume.class, volume.getId());
                    removeApplicationFromVolume(volume.getId(), dbClient);
            }
        }
        if (consistencyGroups != null && !consistencyGroups.isEmpty()) {
            for (URI cguri : consistencyGroups) {
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockConsistencyGroup.class, cguri, coded);
                        break;
                    default:
                        setReadyOnDataObject(dbClient, BlockConsistencyGroup.class, cguri);
                }
            }
        }
        log.info("END ApplicationCompleter complete");
    }
    
    /**
     * Add the application to the volume applicationIds attribute
     * @param voluri The volumes that will be updated
     * @param dbClient
     */
    private void removeApplicationFromVolume(URI voluri, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, voluri);
        String appId = getId().toString();
        StringSet appIds = volume.getVolumeGroupIds();
        if(appIds != null) {
            appIds.remove(appId);
        }
        dbClient.updateObject(volume);
    }
    
    private Volume getVolume(URI voluri, DbClient dbClient) {
        // if this is a vplex volume, update the parent virtual volume
        List<Volume> vplexVolumes = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, Volume.class,
                        getVolumesByAssociatedId(voluri.toString()));
        
        Volume volume = null;

        for (Volume vplexVolume : vplexVolumes) {
            URI storageURI = vplexVolume.getStorageController();
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, storageURI);
            if (DiscoveredDataObject.Type.vplex.name().equals(storage.getSystemType())) {
                volume = vplexVolume;
            }
        }
        
        if (volume == null) {
            volume = dbClient.queryObject(Volume.class, voluri);
        }
        return volume;
    }
    
    /**
     * Add the application to the volume applicationIds attribute
     * @param voluri The volumes that will be updated
     * @param dbClient
     */
    private void addApplicationToVolume(Volume volume, DbClient dbClient) {

        StringSet applications = volume.getVolumeGroupIds();
        if (applications == null) {
            applications = new StringSet();
        }
        applications.add(getId().toString());
        volume.setVolumeGroupIds(applications);
        dbClient.updateObject(volume);
        
    }
}
