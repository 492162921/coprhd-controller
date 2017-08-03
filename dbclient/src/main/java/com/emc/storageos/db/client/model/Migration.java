/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Represents a migration operation for volume or consistency group.
 * For volume migration, source and target fields represents volume.
 * For consistency group migration, source and target represents storage systems.
 */
@ExcludeFromGarbageCollection
@Cf("Migration")
public class Migration extends DataObject {

    // The URI of the volume associated with the migration.
    private URI _volume;

    // The URI of the migration source (volume)
    private URI _source;

    // The URI of the migration target (volume)
    private URI _target;

    // The URI of the consistency group associated with the migration.
    private URI _consistencyGroup;

    // The URI of the migration source system
    private URI _sourceSystem;

    // The URI of the migration target system
    private URI _targetSystem;

    // The Serial number of the migration source system
    private String _sourceSystemSerialNumber;

    // The Serial number of the migration target system
    private String _targetSystemSerialNumber;

    // The migration start time.
    private String _startTime;

    // The migration end time.
    private String _endTime;

    // The status of the migration.
    private String _migrationStatus;

    // The percentage done.
    private String _percentDone;

    // The list of data stores affected.
    private String _dataStoresAffected;

    // The list of SAN zones created.
    private String _zonesCreated;

    // The list of SAN zones re-used.
    private String _zonesReused;

    // The list of initiators involved in migration.
    private String _initiators;

    // The list of target storage ports involved in migration.
    private String _targetStoragePorts;

    /**
     * Getter for the URI of the volume being migrated.
     * 
     * @return The URI of the volume being migrated.
     */
    @RelationIndex(cf = "RelationIndex", type = Volume.class)
    @Name("volume")
    public URI getVolume() {
        return _volume;
    }

    /**
     * Setter for the URI of the volume being migrated.
     * 
     * @param name The URI of the volume being migrated.
     */
    public void setVolume(URI volume) {
        _volume = volume;
        setChanged("volume");
    }

    /**
     * Getter for the URI of the migration source.
     * 
     * @return The URI of the migration source.
     */
    @Name("source")
    public URI getSource() {
        return _source;
    }

    /**
     * Setter for the URI of the migration source.
     * 
     * @param name The URI of the migration source.
     */
    public void setSource(URI source) {
        _source = source;
        setChanged("source");
    }

    /**
     * Getter for the URI of the migration target.
     * 
     * @return The URI of the migration target.
     */
    @Name("target")
    public URI getTarget() {
        return _target;
    }

    /**
     * Setter for the URI of the migration target.
     * 
     * @param name The URI of the migration target.
     */
    public void setTarget(URI target) {
        _target = target;
        setChanged("target");
    }

    /**
     * Getter for the URI of the consistency group being migrated.
     * 
     * @return The URI of the consistency group being migrated.
     */
    @RelationIndex(cf = "RelationIndex", type = BlockConsistencyGroup.class)
    @Name("consistencyGroup")
    public URI getConsistencyGroup() {
        return _consistencyGroup;
    }

    /**
     * Setter for the URI of the consistency group being migrated.
     * 
     * @param name The URI of the consistency group being migrated.
     */
    public void setConsistencyGroup(URI consistencyGroup) {
        _consistencyGroup = consistencyGroup;
        setChanged("consistencyGroup");
    }

    /**
     * Getter for the URI of the migration source system.
     * 
     * @return The URI of the migration source system.
     */
    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("sourceSystem")
    public URI getSourceSystem() {
        return _sourceSystem;
    }

    /**
     * Setter for the URI of the migration source system.
     * 
     * @param name The URI of the migration source system.
     */
    public void setSourceSystem(URI sourceSystem) {
        _sourceSystem = sourceSystem;
        setChanged("sourceSystem");
    }

    /**
     * Getter for the URI of the migration target system.
     * 
     * @return The URI of the migration target system.
     */
    @Name("targetSystem")
    public URI getTargetSystem() {
        return _targetSystem;
    }

    /**
     * Setter for the URI of the migration target system.
     * 
     * @param name The URI of the migration target system.
     */
    public void setTargetSystem(URI targetSystem) {
        _targetSystem = targetSystem;
        setChanged("targetSystem");
    }

    /**
     * Getter for the serial number of the migration source system.
     * 
     * @return The serial number of the migration source system.
     */
    @Name("sourceSystemSerialNumber")
    public String getSourceSystemSerialNumber() {
        return _sourceSystemSerialNumber;
    }

    /**
     * Setter for the serial number of the migration source system.
     * 
     * @param name The serial number of the migration source system.
     */
    public void setSourceSystemSerialNumber(String sourceSystemSerialNumber) {
        _sourceSystemSerialNumber = sourceSystemSerialNumber;
        setChanged("sourceSystemSerialNumber");
    }

    /**
     * Getter for the serial number of the migration target system.
     * 
     * @return The serial number of the migration target system.
     */
    @Name("targetSystemSerialNumber")
    public String getTargetSystemSerialNumber() {
        return _targetSystemSerialNumber;
    }

    /**
     * Setter for the serial number of the migration target system.
     * 
     * @param name The serial number of the migration target system.
     */
    public void setTargetSystemSerialNumber(String targetSystemSerialNumber) {
        _targetSystemSerialNumber = targetSystemSerialNumber;
        setChanged("targetSystemSerialNumber");
    }

    /**
     * Getter for the migration start time.
     * 
     * @return The migration start time.
     */
    @Name("startTime")
    public String getStartTime() {
        return _startTime;
    }

    /**
     * Setter for the migration start time.
     * 
     * @param name The migration start time.
     */
    public void setStartTime(String startTime) {
        _startTime = startTime;
        setChanged("startTime");
    }

    /**
     * Getter for the migration end time.
     * 
     * @return The migration end time.
     */
    @Name("endTime")
    public String getEndTime() {
        return _endTime;
    }

    /**
     * Setter for the migration end time.
     * 
     * @param name The migration end time.
     */
    public void setEndTime(String endTime) {
        _endTime = endTime;
        setChanged("endTime");
    }

    /**
     * Getter for the migration status.
     * 
     * @return The status of the migration.
     */
    @Name("migrationStatus")
    public String getMigrationStatus() {
        return _migrationStatus;
    }

    /**
     * Setter for the migration status.
     * 
     * @param status The status of the migration.
     */
    public void setMigrationStatus(String status) {
        _migrationStatus = status;
        setChanged("migrationStatus");
    }

    /**
     * Getter for the migration percentage done.
     * 
     * @return The migration percentage done.
     */
    @Name("percentDone")
    public String getPercentDone() {
        return _percentDone;
    }

    /**
     * Setter for the migration percentage done.
     * 
     * @param name The migration percentage done.
     */
    public void setPercentDone(String percentDone) {
        _percentDone = percentDone;
        setChanged("percentDone");
    }

    /**
     * Gets the data stores affected.
     *
     * @return the data stores affected
     */
    @Name("dataStoresAffected")
    public String getDataStoresAffected() {
        return _dataStoresAffected;
    }

    /**
     * Sets the data stores affected.
     *
     * @param dataStoresAffected the new data stores affected
     */
    public void setDataStoresAffected(String dataStoresAffected) {
        _dataStoresAffected = dataStoresAffected;
        setChanged("dataStoresAffected");
    }

    /**
     * Gets the zones created.
     *
     * @return the zones created
     */
    @Name("zonesCreated")
    public String getZonesCreated() {
        return _zonesCreated;
    }

    /**
     * Sets the zones created.
     *
     * @param zonesCreated the new zones created
     */
    public void setZonesCreated(String zonesCreated) {
        zonesCreated = _zonesCreated;
        setChanged("zonesCreated");
    }
    
    

    /**
     * Gets the zones reused.
     *
     * @return the zones reused
     */
    @Name("zonesReused")
    public String getZonesReused() {
        return _zonesReused;
    }

    /**
     * Sets the zones reused.
     *
     * @param zonesReused the new zones reused
     */
    public void setZonesReused(String zonesReused) {
        _zonesReused = zonesReused;
        setChanged("zonesReused");
    }

    /**
     * Gets the initiators.
     *
     * @return the initiators
     */
    @Name("initiators")
    public String getInitiators() {
        return _initiators;
    }

    /**
     * Sets the initiators.
     *
     * @param initiators the new initiators
     */
    public void setInitiators(String initiators) {
        _initiators = initiators;
        setChanged("initiators");
    }

    /**
     * Gets the target storage ports.
     *
     * @return the target storage ports
     */
    @Name("targetStoragePorts")
    public String getTargetStoragePorts() {
        return _targetStoragePorts;
    }

    /**
     * Sets the target storage ports.
     *
     * @param targetStoragePorts the new target storage ports
     */
    public void setTargetStoragePorts(String targetStoragePorts) {
        _targetStoragePorts = targetStoragePorts;
        setChanged("targetStoragePorts");
    }
}
