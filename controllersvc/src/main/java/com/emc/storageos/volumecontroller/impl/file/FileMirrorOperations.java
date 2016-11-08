/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;

import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * 
 * File Mirror protection actions interface class
 *
 */
public interface FileMirrorOperations {
    /**
     * Create a mirror for a filesystem
     * 
     * @param system
     *            - URI of storage controller.
     * @param source
     *            - source file system
     * @param target
     *            - target file system
     * @param completer
     *            - task completer
     * @throws DeviceControllerException
     */
    void createMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer)
            throws DeviceControllerException;

    /**
     * Stop Mirror link, this operation will dismantle the replication session and delete the policy.
     * After this operation both File System will be independent and read-write enabled.
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void stopMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Start Mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void startMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException;

    /**
     * Pause Mirror session between between source and target.
     * This operation will cancel any running/paused job and then disable the policy
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     * 
     */
    void pauseMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Resume Mirror link , this operation will enable the policy and start the replication job.
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void resumeMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Failover Mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void failoverMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException;

    /**
     * Failback Mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void failbackMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String policyName)
            throws DeviceControllerException;

    /**
     * Resync the mirror link
     * 
     * @param primarySystem
     * @param secondarySystem
     * @param target
     * @param completer
     * @param policyName
     */
    void resyncMirrorFileShareLink(StorageSystem primarySystem, StorageSystem secondarySystem, FileShare target, TaskCompleter completer,
            String policyName);

    /**
     * Delete Mirror of a filesystem
     * 
     * @param system
     * @param source
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void deleteMirrorFileShareLink(StorageSystem system, URI source, URI target, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Cancel the mirror link
     * 
     * @param system
     * @param target
     * @param completer
     * @param devPolicyName
     * @throws DeviceControllerException
     */
    void cancelMirrorFileShareLink(StorageSystem system, FileShare target, TaskCompleter completer, String devPolicyName)
            throws DeviceControllerException;

    /**
     * Refresh Mirror State of a filesystem
     * 
     * @param system
     * @param source
     * @param target
     * @param completer
     *
     * @throws DeviceControllerException
     */
    void refreshMirrorFileShareLink(StorageSystem system, FileShare source, FileShare target, TaskCompleter completer)
            throws DeviceControllerException;

    /**
     * Update Replication Policy of a filesystem
     * 
     * @param system
     * @param rpoValue
     * @param rpoType
     * @param target
     * @param completer
     * @throws DeviceControllerException
     */
    void doModifyReplicationRPO(StorageSystem system, Long rpoValue, String rpoType, FileShare target, TaskCompleter completer)
            throws DeviceControllerException;
}
