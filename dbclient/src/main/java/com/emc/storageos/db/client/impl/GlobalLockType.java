/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.serializers.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.GlobalLock;

/**
 * Encapsulate Global Lock
 */
public class GlobalLockType {
    private static final Logger log = LoggerFactory.getLogger(GlobalLockType.class);

    private final Class type = GlobalLock.class;
    private ColumnFamily<String, String> cf;

    /**
     * Constructor
     * 
     * @param clazz
     */
    public GlobalLockType() {
        cf = new ColumnFamily<String, String>(((Cf) type.getAnnotation(Cf.class)).value(),
                StringSerializer.get(), StringSerializer.get());
    }

    /**
     * Get CF for global lock
     * 
     * @return
     */
    public ColumnFamily<String, String> getCf() {
        return cf;
    }

    public void serialize(RowMutator mutator, GlobalLock glock) throws ConnectionException {
        mutator.insertGlobalLockRecord(cf.getName(), glock.getName(), GlobalLock.GL_MODE_COLUMN, glock.getMode(), null);
        mutator.insertGlobalLockRecord(cf.getName(), glock.getName(), GlobalLock.GL_OWNER_COLUMN, glock.getOwner(), null);
        mutator.insertGlobalLockRecord(cf.getName(), glock.getName(), GlobalLock.GL_EXPIRATION_COLUMN, glock.getExpirationTime(), null);
        mutator.execute();
    }

    public GlobalLock deserialize(Row<String, String> row) {
        if (row == null) {
            return null;
        }

        ColumnList<String> columnList = row.getColumns();
        if (columnList == null || columnList.isEmpty()) {
            return null;
        }

        Column<String> mode = columnList.getColumnByName(GlobalLock.GL_MODE_COLUMN);
        Column<String> owner = columnList.getColumnByName(GlobalLock.GL_OWNER_COLUMN);
        Column<String> expiration = columnList.getColumnByName(GlobalLock.GL_EXPIRATION_COLUMN);

        GlobalLock glock = new GlobalLock();
        glock.setName(row.getKey());
        glock.setMode(mode.getStringValue());
        glock.setOwner(owner.getStringValue());
        glock.setExpirationTime(expiration.getStringValue());

        return glock;
    }
}
