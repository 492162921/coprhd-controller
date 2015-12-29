package com.emc.storageos.driver.scaleio;

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ScaleIOHelper {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOHelper.class);

    /**
     * Generate Task ID for a task type
     * 
     * @param taskType
     * @return
     */
    public static String getTaskId(ScaleIOConstants.TaskType taskType) {
        return String.format("%s+%s+%s", ScaleIOConstants.DRIVER_NAME, taskType.name(), UUID.randomUUID());
    }

    /**
     * Generate timestamp
     * 
     * @return
     */
    public static String getCurrentTime() {
        DateFormat datafomate = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
        Date date = new Date();
        return datafomate.format(date);
    }

    /**
     * Check if all snapshots are from same storage system
     * 
     * @param snapshots
     * @return
     */
    public static boolean isFromSameStorageSystem(List<VolumeSnapshot> snapshots) {
        boolean isSameSys = false;
        if (snapshots != null) {
            String storageSystemId = snapshots.get(0).getStorageSystemId();
            isSameSys = true;
            for (VolumeSnapshot snapshot : snapshots) {
                if (snapshot.getStorageSystemId() != storageSystemId) {
                    isSameSys = false;
                    break;
                }
            }
        }
        return isSameSys;
    }

    /**
     * Check if all snapshots are from same consistency group
     * 
     * @param snapshots
     * @return
     */
    public static boolean isFromSameCGgroup(List<VolumeSnapshot> snapshots) {
        boolean isSameCG = false;
        if (snapshots != null) {
            String groupId = snapshots.get(0).getConsistencyGroup();
            isSameCG = true;
            for (VolumeSnapshot snapshot : snapshots) {
                if (snapshot.getConsistencyGroup() != groupId) {
                    isSameCG = false;
                    break;
                }
            }
        }
        return isSameCG;
    }
}
