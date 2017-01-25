package com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

public class RemoteReplicationFailoverCompleter extends TaskCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(RemoteReplicationFailoverCompleter.class);

    private DbClient dbClient;

    protected void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    protected DbClient getDbClient() {
        return dbClient;
    }

    private RemoteReplicationSet.ElementType elementType;
    private URI elementURI;
    /**
     * Constructor for failover completer
     *
     * @param remoteReplicationElement
     * @param opId
     */
    public RemoteReplicationFailoverCompleter(RemoteReplicationElement remoteReplicationElement, String opId) {
        elementType = remoteReplicationElement.getType();
        elementURI = remoteReplicationElement.getElementUri();

        setOpId(opId);
        addIds(Collections.singletonList(elementURI));
        switch (elementType) {
            case REPLICATION_GROUP:
                setType(RemoteReplicationGroup.class);
                break;
            case REPLICATION_PAIR:
                break;
            case REPLICATION_SET:
                break;
        }
    }


    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        setDbClient(dbClient);

        _logger.info("Complete operation for {} with id {} and status {}", elementType, elementURI, status);
        try {
            if (status == Operation.Status.ready) {
                switch (elementType) {
                    case REPLICATION_GROUP:
                        RemoteReplicationGroup remoteReplicationGroup = dbClient.queryObject(RemoteReplicationGroup.class, elementURI);
                        _logger.info("Failed over group: {}", remoteReplicationGroup.getNativeId());
                        List<RemoteReplicationPair> rrPairs = CustomQueryUtility.queryActiveResourcesByRelation(dbClient, elementURI,
                                RemoteReplicationPair.class, "replicationGroup");
                        for (RemoteReplicationPair rrPair : rrPairs) {
                            rrPair.setReplicationState(RemoteReplicationSet.ReplicationState.FAILED_OVER.toString());
                        }
                        remoteReplicationGroup.setReplicationState(RemoteReplicationSet.ReplicationState.FAILED_OVER.toString());
                        dbClient.updateObject(remoteReplicationGroup);
                        dbClient.updateObject(rrPairs);
                        _logger.info("Completed operation for {} with id {} and status {}", elementType, elementURI, status);
                        break;
                    case REPLICATION_PAIR:
                        break;
                    case REPLICATION_SET:
                        break;
                }
            }
        } catch (Exception e) {
            _logger.error(String.format(
                    "Failed to process failover completion for %s with Id: %s, OpId: %s",
                    elementType, elementURI, getOpId()), e);

        } finally {
            setStatus(dbClient, status, coded);
            updateWorkflowStatus(status, coded);
        }
    }

}
