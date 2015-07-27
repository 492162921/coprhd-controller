/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class BlockConsistencyGroupDeleteCompleter extends BlockConsistencyGroupTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockConsistencyGroupDeleteCompleter.class);
    public static final String CONSISTENCY_GROUP_DELETED_MSG = "Consistency group %s deleted";
    public static final String CONSISTENCY_GROUP_DELETE_FAILED = "Failed to delete consistency group %s";

    public BlockConsistencyGroupDeleteCompleter(URI consistencyGroup, String opId) {
        super(BlockConsistencyGroup.class, consistencyGroup, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) {
        try {
            super.complete(dbClient, status, coded);
            BlockConsistencyGroup consistencyGroup = dbClient.queryObject(BlockConsistencyGroup.class, getConsistencyGroupURI());

            switch (status) {
            case error:
                dbClient.error(BlockConsistencyGroup.class, consistencyGroup.getId(), getOpId(),
                        coded);
                break;
            default:
                dbClient.ready(BlockConsistencyGroup.class, consistencyGroup.getId(), getOpId());
            }

            recordBourneBlockConsistencyGroupEvent(dbClient, consistencyGroup.getId(), eventType(status),
                    status, eventMessage(status, consistencyGroup));
        } catch (Exception e) {
            _log.error("Failed updating status. BlockConsistencyGroupDelete {}, for task " + getOpId(), getId(), e);
        }
    }

    private RecordableEventManager.EventType eventType(Operation.Status status) {
        return (Operation.Status.ready == status) ?
                RecordableEventManager.EventType.ConsistencyGroupDeleted :
                RecordableEventManager.EventType.ConsistencyGroupDeleteFailed;
    }

    private String eventMessage(Operation.Status status, BlockConsistencyGroup consistencyGroup) {
        return (Operation.Status.ready == status) ?
                String.format(CONSISTENCY_GROUP_DELETED_MSG, consistencyGroup.getLabel()) :
                String.format(CONSISTENCY_GROUP_DELETE_FAILED, consistencyGroup.getLabel());
    }
}
