/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.model.Column;

/**
 * Utility class for accumulating obsolete columns during deserialization
 */
public class IndexCleanupList implements IndexColumnList {

    private static final Logger _log = LoggerFactory.getLogger(IndexCleanupList.class);

    public static final CompositeColumnName INACTIVE_COLUMN = new CompositeColumnName(DataObject.INACTIVE_FIELD_NAME);

    private Map<String, List<Column<CompositeColumnName>>> _cleanupList;
    private Map<String, Map<CompositeColumnName, Column>> _currentMap;
    // _allColMap is a union of _cleanupList and _currentMap. It might be a redundant structure
    // but it easier to operate.
    private Map<String, Map<String, List<Column<CompositeColumnName>>>> _allColMap;
    private Map<String, DataObject> _cleanedObjects;

    public IndexCleanupList() {
        _cleanupList = new HashMap<>();
        _currentMap = new HashMap<>();
        _allColMap = new HashMap<>();
        _cleanedObjects = new HashMap<>();
    }

    @Override
    public void add(String key, Column<CompositeColumnName> column) {
        Map<CompositeColumnName, Column> colMap = _currentMap.get(key);
        Map<String, List<Column<CompositeColumnName>>> keyColumns = _allColMap.get(key);
        if (colMap == null) {
            colMap = new HashMap<>();
            _currentMap.put(key, colMap);
            keyColumns = new HashMap<>();
            _allColMap.put(key, keyColumns);
        }
        Column previousCol = colMap.put(column.getName(), column);
        if (previousCol != null) {
            // If .inactive is already true, it's not allowed to be set back to false
            if (column.getName().getOne().equals(INACTIVE_COLUMN.getOne())) {
                if (previousCol.getBooleanValue() && !column.getBooleanValue()) {
                    // Switching from true (inactive) to false (active), which is not allowed.
                    // throw new
                    // IllegalStateException(String.format("Row with ID \"%s\" contains a change to \"inactive\" field from true to false, which is not supported",
                    // key));
                    _log.error(String.format("Row with ID '%s' is changed from inactive to active.", key), new RuntimeException());
                }
            }

            List<Column<CompositeColumnName>> cleanList = _cleanupList.get(key);
            if (cleanList == null) {
                cleanList = new ArrayList<>();
                _cleanupList.put(key, cleanList);
            }
            cleanList.add(previousCol);
            if (ColumnField.isDeletionMark(column)) {
                cleanList.add(column);
            }
        }
        String colName = column.getName().getOne();
        List<Column<CompositeColumnName>> columns = keyColumns.get(colName);
        if (columns == null) {
            columns = new ArrayList<>();
            keyColumns.put(colName, columns);
        }
        columns.add(column);
    }

    @Override
    public Map<String, List<Column<CompositeColumnName>>> getColumnsToClean() {
        return Collections.unmodifiableMap(_cleanupList);
    }

    @Override
    public Map<String, List<Column<CompositeColumnName>>> getAllColumns(String key) {
        return Collections.unmodifiableMap(_allColMap.get(key));
    }

    @Override
    public boolean isEmpty() {
        return _cleanupList.isEmpty();
    }

    // Returns a map contains <RowKey, Columns need to remove their index entries because .inactive is true>
    // If changedOnly is true, we only returns rows those have indexed fields changed
    // If changedOnly is false, we returns all touched rows with .inactive = true, even no field is actually changed
    public Map<String, List<Column<CompositeColumnName>>> getIndexesToClean(boolean changedOnly) {

        Map<String, List<Column<CompositeColumnName>>> mapIndexes = new HashMap<>();

        // For each object we have touched
        for (Map.Entry<String, Map<CompositeColumnName, Column>> entry : _currentMap.entrySet()) {
            String rowKey = entry.getKey();
            Map<CompositeColumnName, Column> colMap = entry.getValue();

            if (changedOnly && !_cleanupList.containsKey(rowKey)) {
                continue;
            }

            // Check if this row's final "inactive" column is "true"
            Column inactiveColumn = colMap.get(INACTIVE_COLUMN);
            if (inactiveColumn != null && inactiveColumn.getBooleanValue()) {
                ArrayList<Column<CompositeColumnName>> cols = new ArrayList<Column<CompositeColumnName>>();

                for (Column<CompositeColumnName> col : colMap.values()) {
                    // All indexed fields except "inactive" itself need to be removed
                    if (!col.getName().getOne().equals("inactive")
                            && col.getName().getTimeUUID() != null
                            && !ColumnField.isDeletionMark(col)) {
                        cols.add(col);
                    }
                }

                if (!cols.isEmpty()) {
                    mapIndexes.put(rowKey, cols);
                }
            }
        }

        return mapIndexes;
    }

    public void addObject(String key, DataObject object) {
        _cleanedObjects.put(key, object);
    }

    public DataObject getObject(String key) {
        return _cleanedObjects.get(key);
    }
}
