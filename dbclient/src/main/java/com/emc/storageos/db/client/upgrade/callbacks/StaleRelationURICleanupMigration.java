/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.CqlResult;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.serializers.StringSerializer;

/**
 * This migration handler is for COP-27666
 * Invalid relationships: Object A points to object B, but B is not existed or inactive.
 * This migration handler will clean up such invalid relationships.For now, focus on 
 * ExportMask/ExportGroup since most known customers issues are related to those 2 objects
 */
public class StaleRelationURICleanupMigration extends BaseCustomMigrationCallback{
    private static final Logger log = LoggerFactory.getLogger(StaleRelationURICleanupMigration.class);
    private static final String CQL_QUERY_ACTIVE_URI = "select * from \"%s\" where key in (%s) and column1='inactive'";
    private Map<Class<? extends DataObject>, List<RelationField>> relationFields = new HashMap<>();
    private int totalStaleURICount = 0;
    private int totalModifiedObject = 0;
    
    @Override
    public void process() throws MigrationCallbackException {
        initRelationFields();
        DbClientImpl dbClient = (DbClientImpl)getDbClient();
        
        for (Entry<Class<? extends DataObject>, List<RelationField>> entry : relationFields.entrySet()) {
            DataObjectType doType = TypeMap.getDoType(entry.getKey());
            
            //for each class, query out all objects iteratively
            List<URI> uriList = dbClient.queryByType(entry.getKey(), true);
            Iterator<DataObject> resultIterator = (Iterator<DataObject>) dbClient.queryIterativeObjects(entry.getKey(), uriList, true);
            
            while (resultIterator.hasNext()) {
                DataObject dataObject = resultIterator.next();
                boolean isChanged = false;
                for (RelationField relationField : entry.getValue()) {
                    isChanged |= doRelationURICleanup(doType, dataObject, relationField);
                }
                
                if (isChanged) {
                    totalModifiedObject++;
                    dbClient.updateObject(dataObject);
                }
            }
        }
        
        log.info("Totally found {} stale/invalid URI keys", totalStaleURICount);
        log.info("Totally {} data objects have been modifed to remove stale/invalid URI", totalModifiedObject);
    }

    protected boolean doRelationURICleanup(DataObjectType doType, DataObject dataObject, RelationField relationField) {
        boolean isChanged = false;
        try {
            ColumnField columnField = doType.getColumnField(relationField.getFieldName());
            Object fieldValue = columnField.getFieldValue(columnField, dataObject);
            
            List<String> relationURIList = getURIListFromDataObject(fieldValue);
            List<String> validRelationURIList = queryValidRelationURIList((DbClientImpl)getDbClient(), relationField, relationURIList);
            Collection<String> invalidURIs = CollectionUtils.subtract(relationURIList, validRelationURIList);//get invalid URI list
            
            if (!invalidURIs.isEmpty()) {
                totalStaleURICount += invalidURIs.size();
                log.info("Stale/invalid URI found for class: {}, key: {}, field: {}", doType.getDataObjectClass().getSimpleName(), dataObject.getId(), relationField);
                log.info(StringUtils.join(invalidURIs, ","));
                isChanged = true;
                saveNewURIListToObject(dataObject, columnField, fieldValue, invalidURIs);
            }
        } catch (Exception e) {
            log.error("Failed to run migration handler for class{}, {}", doType.getDataObjectClass().getSimpleName(), relationField, e);
        }
        return isChanged;
    }

    private void saveNewURIListToObject(DataObject dataObject, ColumnField columnField, Object fieldValue,
            Collection<String> invalidRelationURIList) throws IllegalAccessException, InvocationTargetException {
        for (String invalidURI : invalidRelationURIList) {
            if (fieldValue instanceof StringSet) {
                ((StringSet)fieldValue).remove(invalidURI);
            } else if (fieldValue instanceof StringMap) {
                ((StringMap)fieldValue).remove(invalidURI);
            }
        }
    }

    private List<String> queryValidRelationURIList(DbClientImpl dbClient, RelationField relationField, List<String> relationURIList) throws ConnectionException {
        List<String> validRelationURIList = new ArrayList<>();
        if (relationURIList.isEmpty()) {
            return validRelationURIList;
        }
        
        ColumnFamily<String, String> targetCF =
                new ColumnFamily<String, String>(TypeMap.getDoType(relationField.getTargetCF()).getCF().getName(),
                        StringSerializer.get(), StringSerializer.get(), StringSerializer.get());
        OperationResult<CqlResult<String, String>> queryResult;
        
        StringBuilder keyString = new StringBuilder();
        for (String uri : relationURIList) {
            if (keyString.length() > 0) {
                keyString.append(", ");
            }
            keyString.append("'").append(uri.toString()).append("'");
        }
        
        //to get better performance, only query key and inactive fields by CQL here
        //Thrift can't help to determine whether key exists or not
        queryResult = dbClient.getLocalContext()
                .getKeyspace().prepareQuery(targetCF)
                .withCql(String.format(CQL_QUERY_ACTIVE_URI, targetCF.getName(), keyString))
                .execute();
        
        //only inactive=true and existing key will be added as valid URI 
        for (Row<String, String> row : queryResult.getResult().getRows()) {
            ColumnList<String> columns = row.getColumns();
            if (!columns.getBooleanValue("value", false)) {
                validRelationURIList.add(row.getColumns().getColumnByIndex(0).getStringValue());
            }
        }
        
        return validRelationURIList;
    }

    private List<String> getURIListFromDataObject(Object fieldValue) {
        List<String> relactionURIList = new ArrayList<>();
        if (fieldValue instanceof Set) {
            relactionURIList.addAll((Set)fieldValue);
        } else if (fieldValue instanceof Map) {
            relactionURIList.addAll(((Map)fieldValue).keySet());
        }
        return relactionURIList;
    }
    
    //Currently only focus on ExporMask and ExporGroup
    //We can consider to put these information into XML file 
    //if thera are more model classes to be handled in future
    private void initRelationFields() {
        List<RelationField> fields = new ArrayList<>();
        fields.add(new RelationField("initiators", Initiator.class));
        fields.add(new RelationField("hosts", Host.class));
        fields.add(new RelationField("volumes", Volume.class));
        fields.add(new RelationField("snapshots", BlockSnapshot.class));
        fields.add(new RelationField("clusters", Cluster.class));
        fields.add(new RelationField("exportMasks", ExportMask.class));
        relationFields.put(ExportGroup.class, fields);
        
        fields = new ArrayList<>();
        fields.add(new RelationField("initiators", Initiator.class));
        fields.add(new RelationField("storagePorts", StoragePort.class));
        fields.add(new RelationField("volumes", Volume.class));
        relationFields.put(ExportMask.class, fields);
    }
    
    private static class RelationField {
        private String fieldName;
        private Class<? extends DataObject> targetCF;

        public RelationField(String fieldName, Class<? extends DataObject> targetCF) {
            super();
            this.fieldName = fieldName;
            this.targetCF = targetCF;
        }

        public String getFieldName() {
            return fieldName;
        }

        public Class<? extends DataObject> getTargetCF() {
            return targetCF;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("RelationField [fieldName=");
            builder.append(fieldName);
            builder.append(", targetCF=");
            builder.append(targetCF);
            builder.append("]");
            return builder.toString();
        }
    }
}
