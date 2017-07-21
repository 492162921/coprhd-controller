/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.driversimulator;


import com.emc.storageos.storagedriver.storagecapabilities.CapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.CommonStorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.HostIOLimitsCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilitiesUtils;
import com.emc.storageos.storagedriver.storagecapabilities.VolumeCompressionCapabilityDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for storage driver simulator
 */
public class StorageDriverSimulatorUtils {

    private static int count =0;

    public static CapabilityInstance getHostIOLimitsCapabilities(StorageCapabilities storageCapabilities) {
        // get hostio limits capability
        CapabilityInstance hostIOLimits = null;
        CommonStorageCapabilities commonCapabilities = storageCapabilities.getCommonCapabilities();
        if (commonCapabilities != null) {
            hostIOLimits = StorageCapabilitiesUtils.getDataStorageServiceCapability(commonCapabilities, CapabilityDefinition.CapabilityUid.hostIOLimits);
        }
        return hostIOLimits;
    }

    public static CapabilityInstance getVolumeCompressionCapabilities(StorageCapabilities storageCapabilities) {
        // get volume compression capability
        CapabilityInstance volumeCompression = null;
        CommonStorageCapabilities commonCapabilities = storageCapabilities.getCommonCapabilities();
        if (commonCapabilities != null) {
            volumeCompression = StorageCapabilitiesUtils.getDataStorageServiceCapability(commonCapabilities, CapabilityDefinition.CapabilityUid.volumeCompression);
        }
        return volumeCompression;
    }

    public static void addHostIOLimitsCapabilities(CommonStorageCapabilities commonCapabilities) {

        int host_io_limits_iops;
        host_io_limits_iops = ++count%2 == 0 ? 100 : 200;

        HostIOLimitsCapabilityDefinition capabilityDefinition = new HostIOLimitsCapabilityDefinition();
        Map<String, List<String>> capabilityProperties = new HashMap<>();
        capabilityProperties.put(HostIOLimitsCapabilityDefinition.PROPERTY_NAME.HOST_IO_LIMIT_BANDWIDTH.name(),
                Collections.singletonList("100"));
        capabilityProperties.put(HostIOLimitsCapabilityDefinition.PROPERTY_NAME.HOST_IO_LIMIT_IOPS.name(),
                Collections.singletonList(Integer.toString(host_io_limits_iops)));

        CapabilityInstance hostIOLimitsCapability = new CapabilityInstance(capabilityDefinition.getId(),
                capabilityDefinition.getId(), capabilityProperties);

        StorageCapabilitiesUtils.addDataStorageServiceOption(commonCapabilities, Collections.singletonList(hostIOLimitsCapability));
    }

    public static void addVolumeCompressionCapability(CommonStorageCapabilities commonCapabilities) {

        VolumeCompressionCapabilityDefinition capabilityDefinition = new VolumeCompressionCapabilityDefinition();
        Map<String, List<String>> capabilityProperties = new HashMap<>();
        capabilityProperties.put(VolumeCompressionCapabilityDefinition.PROPERTY_NAME.COMPRESSION_RATIO.name(),
                Collections.singletonList("10"));
        capabilityProperties.put(VolumeCompressionCapabilityDefinition.PROPERTY_NAME.ENABLED.name(),
                Collections.singletonList("true"));

        CapabilityInstance volumeCompressionCapability = new CapabilityInstance(capabilityDefinition.getId(),
                capabilityDefinition.getId(), capabilityProperties);

        StorageCapabilitiesUtils.addDataStorageServiceOption(commonCapabilities, Collections.singletonList(volumeCompressionCapability));
    }
}
