/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * Migration handler to convert value of type field for an export group, from
 * Exclusive to Initiator.
 */
public class ExportGroupTypeConverter extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory
            .getLogger(ExportGroupTypeConverter.class);
    private static final String TYPE_FIELD_NAME = "type";
    private static final String OLD_TYPE_VALUE = "Exclusive";

    @Override
    public void process() {
        log.info("Handle ExportGroup type conversion");
        DbClient dbClient = getDbClient();
        List<URI> exportGroupURIs = dbClient.queryByType(ExportGroup.class,
                false);
        Iterator<ExportGroup> exportGroupsIter = dbClient
                .queryIterativeObjectField(ExportGroup.class, TYPE_FIELD_NAME, exportGroupURIs);
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();
        while (exportGroupsIter.hasNext()) {
            ExportGroup exportGroup = exportGroupsIter.next();
            String exportGroupId = exportGroup.getId().toString();
            log.info("Examining ExportGroup (id={}) for upgrade", exportGroupId);
            String type = exportGroup.getType();
            if (type.equals(OLD_TYPE_VALUE)) {
                exportGroup.setType(ExportGroupType.Initiator.name());
                log.info("Reset export group type for export group (id={})",
                        exportGroupId);
                exportGroups.add(exportGroup);
            }
        }

        dbClient.persistObject(exportGroups);
    }
}