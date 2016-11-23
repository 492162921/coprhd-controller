/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.common.schema.DbSchema.IndexCFKey;
import com.google.common.base.Objects;

@XmlRootElement(name = "dbschemas")
public class DbSchemas {
    private static final Logger log = LoggerFactory.getLogger(DbSchemas.class);

    private List<DbSchema> schemas = new ArrayList<>();

    // empty ctor for JAXB
    public DbSchemas() {
    }

    public DbSchemas(List<DbSchema> schemas) {
        this.schemas = schemas;
    }

    @XmlElements({
            @XmlElement(name = "data_object_schema", type = DataObjectSchema.class),
            @XmlElement(name = "time_series_schema", type = TimeSeriesSchema.class),
            @XmlElement(name = "data_point_schema", type = DataPointSchema.class)
    })
    public List<DbSchema> getSchemas() {
        return schemas;
    }

    public void setSchemas(List<DbSchema> schemas) {
        this.schemas = schemas;
    }

    public void addSchema(DbSchema schema) {
        schemas.add(schema);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DbSchemas)) {
            return false;
        }

        DbSchemas schemas = (DbSchemas) o;

        return Objects.equal(this.schemas, schemas.getSchemas());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(schemas);
    }

    public boolean hasDuplicateField() {
        for (DbSchema dbSchema : schemas) {
            if (dbSchema.hasDuplicateField()) {
                return true;
            }
        }
        return false;
    }

    public Map<String, List<FieldInfo>> getDuplicateFields() {
        Map<String, List<FieldInfo>> schemaDuplicateColumns = new HashMap<String, List<FieldInfo>>();

        for (DbSchema dbSchema : schemas) {
            if (dbSchema.hasDuplicateField()) {
                schemaDuplicateColumns.put(dbSchema.getName(), dbSchema.getDuplicateFields());
            }
        }

        return schemaDuplicateColumns;
    }
    
    public boolean hasDuplicatedIndexNames() {
        for (DbSchema dbSchema : schemas) {
            if (dbSchema.hasDuplicateIndexCFNames()) {
                return true;
            }
        }
        return false;
    }
    
    public Map<String, Map<IndexCFKey, List<ColumnField>>> getDuplicatedIndexNames() {
        Map<String, Map<IndexCFKey, List<ColumnField>>> duplicateIndexCFByClass = new TreeMap<String, Map<IndexCFKey, List<ColumnField>>>();
        
        for (DbSchema dbSchema : schemas) {
            if (dbSchema.hasDuplicateIndexCFNames()) {
                duplicateIndexCFByClass.put(dbSchema.getModelClass().getName(), dbSchema.getDuplicateIndexCFNames());
            }
        }
        
        return duplicateIndexCFByClass;
    }
}
