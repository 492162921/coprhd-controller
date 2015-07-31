/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.model.DiscoveredDataObject;

public class VmaxCapacityCalculator implements CapacityCalculator {
    private static final long tracksPerCylinder = 15;
    private static final long blocksPerTrack = 128;
    private static final long bytesPerBlock = 512;
    private static final long bytesPerCylinder =
            (tracksPerCylinder * blocksPerTrack * bytesPerBlock);

    /**
     * {@inheritDoc}
     */
    @Override
    public Long calculateAllocatedCapacity(Long requestedCapacity) {
        if (requestedCapacity != null) {
            long cyls = (long) Math.ceil((double) requestedCapacity / bytesPerCylinder);
            return (cyls * bytesPerCylinder);
        }

        return requestedCapacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean capacitiesCanMatch(String storageSystemType) {
        if (storageSystemType.equalsIgnoreCase(DiscoveredDataObject.Type.xtremio.name())) {
            return false;
        }
        return true;
    }
}
