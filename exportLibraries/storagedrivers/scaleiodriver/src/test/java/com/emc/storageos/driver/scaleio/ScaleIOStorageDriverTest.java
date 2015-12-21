package com.emc.storageos.driver.scaleio;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.InMemoryRegistryImpl;
import com.emc.storageos.storagedriver.model.*;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

@RunWith(value = SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/scaleio-driver-prov.xml" })
public class ScaleIOStorageDriverTest {

    String SYS_NATIVE_ID_A = "6ee6d94e5a3517b8";
    String SYS_NATIVE_ID_B = "3eb4708d2b3ea454";
    String IP_ADDRESS_A = "10.193.17.97";
    String IP_ADDRESS_B = "10.193.17.35";
    int PORT_NUMBER = 443;
    String USER_NAME = "admin";
    String PASSWORD = "Scaleio123";
    private ScaleIOStorageDriver driver;
    @Autowired
    private ScaleIORestHandleFactory handleFactory;
    private DriverTask task;

    @Before
    public void setUp() throws Exception {
        Registry registry = new InMemoryRegistryImpl();
        driver = new ScaleIOStorageDriver();
        driver.setHandleFactory(handleFactory);
        driver.setDriverRegistry(registry);
    }

    @Test
    public void testCreateVolumes() throws Exception {
        List<StorageVolume> storageVolumes = new ArrayList<>();
        StorageCapabilities capabilities = null;

        //Create one or more volumes of varying sizes
        Random random = new Random();
        int numVolumes = random.nextInt(10) + 1;

        for (int i = 0; i < numVolumes; i++) {
            Long requestedCapacity = Long.valueOf(random.nextInt(100) + 1);

            StorageVolume volume = new StorageVolume();
            volume.setStorageSystemId("a817f58300000000");
            volume.setStoragePoolId("84c44afd00000000");
            volume.setRequestedCapacity(requestedCapacity);

            storageVolumes.add(volume);
        }

        task = (DriverTaskImpl) driver.createVolumes(storageVolumes, capabilities);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        storageVolumes.clear();

        //Create volume with invalid size
        StorageVolume volume = new StorageVolume();

        volume.setStorageSystemId("a817f58300000000");
        volume.setStoragePoolId("84c44afd00000000");
        volume.setRequestedCapacity(Long.valueOf(-2));

        storageVolumes.add(volume);

        task = (DriverTaskImpl) driver.createVolumes(storageVolumes, capabilities);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "FAILED");

        /* Create volume without connectivity? */
    }

    @Test
    public void testExpandVolume() throws Exception {
        StorageVolume volume = new StorageVolume();
        volume.setStorageSystemId("a817f58300000000");
        volume.setStoragePoolId("84c44afd00000000");
        volume.setRequestedCapacity(Long.valueOf(10));
        volume.setThinVolumePreAllocationSize(Long.valueOf(10));

        Long capacity = volume.getProvisionedCapacity();
        System.out.println(capacity);
        capacity += 100;

        //Expand storage volume
        task = (DriverTaskImpl) driver.expandVolume(volume, capacity);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        //Expand storage volume to invalid size
        task = (DriverTaskImpl) driver.expandVolume(volume, -100);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "FAILED");

        //Expand storage volume that does not already exist in the storage system
        StorageVolume newVolume = new StorageVolume();

        task = (DriverTaskImpl) driver.expandVolume(newVolume, capacity);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "FAILED");

        /* Expand storage volume w/o connectivity? */
    }

    @Test
    public void testDeleteVolumes() throws Exception {
        List<StorageVolume> storageVolumes = new ArrayList<>();
        StorageVolume volume1 = new StorageVolume();
        StorageVolume volume2 = new StorageVolume();

        volume1.setStorageSystemId("a817f58300000000");
        volume1.setStoragePoolId("84c44afd00000000");
        volume1.setRequestedCapacity(Long.valueOf(20));
        volume1.setThinVolumePreAllocationSize(Long.valueOf(20));

        volume2.setStorageSystemId("a817f58300000000");
        volume2.setStoragePoolId("84c44afd00000000");
        volume2.setRequestedCapacity(Long.valueOf(10));
        volume2.setThinVolumePreAllocationSize(Long.valueOf(10));

        storageVolumes.add(volume1);
        storageVolumes.add(volume2);

        //Delete storage volumes
        task = (DriverTaskImpl) driver.deleteVolumes(storageVolumes);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        //Delete storage volume that does not already exist in the storage system
        StorageVolume newVolume = new StorageVolume();
        storageVolumes.clear();
        storageVolumes.add(newVolume);

        task = (DriverTaskImpl) driver.deleteVolumes(storageVolumes);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "FAILED");

        /* Delete a storage volume w/o connectivity? */
    }

    @Test
    public void testDiscoverStorageSystem() throws Exception {
        List<StorageSystem> storageSystems = new ArrayList<>();
        StorageSystem validStorageSystem = new StorageSystem();
        StorageSystem invalidStorageSystem = new StorageSystem();

        validStorageSystem.setSystemName("TestValidSystem");
        validStorageSystem.setSystemType("scaleio");
        // validStorageSystem.setPortNumber();
        validStorageSystem.setUsername("admin");
        validStorageSystem.setPassword("Scaleio123");
        validStorageSystem.setIpAddress("10.193.17.97");

        // Valid list of storage systems
        storageSystems.add(validStorageSystem);

        task = driver.discoverStorageSystem(storageSystems);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        // invalidStorageSystem.setPortNumber();
        invalidStorageSystem.setUsername("username");
        invalidStorageSystem.setPassword("password");
        invalidStorageSystem.setIpAddress("10.193.17.99");

        // Partially valid list of storage systems
        storageSystems.add(invalidStorageSystem);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Invalid list of storage systems
        storageSystems.remove(0);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Empty list of storage systems
        storageSystems.remove(0);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

    }

    @Test
    public void testDiscoverStoragePools() throws Exception {
        StorageSystem validStorageSystem = new StorageSystem();
        StorageSystem invalidStorageSystem = new StorageSystem();

        validStorageSystem.setSystemName("TestValidSystem");
        validStorageSystem.setSystemType("scaleio");
        // validStorageSystem.setPortNumber();
        validStorageSystem.setUsername("admin");
        validStorageSystem.setPassword("Scaleio123");
        validStorageSystem.setIpAddress("10.193.17.97");

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        // invalidStorageSystem.setPortNumber();
        invalidStorageSystem.setUsername("username");
        invalidStorageSystem.setPassword("password");
        invalidStorageSystem.setIpAddress("10.193.17.99");

        List<StoragePool> storagePools = new ArrayList<>();
        StoragePool storagePool = new StoragePool();
        storagePools.add(storagePool);

        task = driver.discoverStoragePools(validStorageSystem, storagePools);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        task = driver.discoverStoragePools(invalidStorageSystem, storagePools);
        System.out.println(task);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");
    }

    @Test
    public void testDiscoverStoragePorts() throws Exception {
        StorageSystem validStorageSystem = new StorageSystem();
        StorageSystem invalidStorageSystem = new StorageSystem();
        List<StoragePort> storagePorts = new ArrayList<>();

        validStorageSystem.setSystemName("TestValidSystem");
        validStorageSystem.setSystemType("scaleio");
        // validStorageSystem.setPortNumber();
        validStorageSystem.setUsername("admin");
        validStorageSystem.setPassword("Scaleio123");
        validStorageSystem.setIpAddress("10.193.17.97");

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        // invalidStorageSystem.setPortNumber();
        invalidStorageSystem.setUsername("username");
        invalidStorageSystem.setPassword("password");
        invalidStorageSystem.setIpAddress("10.193.17.99");

        // Valid system, empty list
        task = driver.discoverStoragePorts(validStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Valid system, invalid list
        task = driver.discoverStoragePorts(validStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertNotEquals(task.getStatus().toString(), "READY");

        // Valid system, valid list
        task = driver.discoverStoragePorts(validStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Invalid system
        task = driver.discoverStoragePorts(invalidStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertNotEquals(task.getStatus().toString(), "READY");

    }

    @Test
    public void testGetConnInfoFromRegistry() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        Assert.assertEquals(IP_ADDRESS_A, driver.getConnInfoFromRegistry(SYS_NATIVE_ID_A, ScaleIOConstants.IP_ADDRESS));
        Assert.assertEquals(Integer.toString(PORT_NUMBER), driver.getConnInfoFromRegistry(SYS_NATIVE_ID_A, ScaleIOConstants.PORT_NUMBER));
        Assert.assertEquals(USER_NAME, driver.getConnInfoFromRegistry(SYS_NATIVE_ID_A, ScaleIOConstants.USER_NAME));
        Assert.assertEquals(PASSWORD, driver.getConnInfoFromRegistry(SYS_NATIVE_ID_A, ScaleIOConstants.PASSWORD));
    }

    @Test
    public void testCreateVolumeSnapshot() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        // null
        List<VolumeSnapshot> snapshots = null;
        DriverTask task = driver.createVolumeSnapshot(snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // create snapshots for volumes from same storage system
        snapshots = new LinkedList<>();
        snapshots.add(initializeSnapshot(null, "d584a34000000000", SYS_NATIVE_ID_A));
        // create snapshot from volume which already has a snapshot
        snapshots.add(initializeSnapshot(null, "d584a34300000002", SYS_NATIVE_ID_A));
        task = driver.createVolumeSnapshot(snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());
        for (VolumeSnapshot snapshot : snapshots) {
            Assert.assertNotNull(snapshot.getNativeId());
        }

        // create snapshots for volumes from different storage systems
        snapshots.removeAll(snapshots);
        snapshots.add(initializeSnapshot(null, "d584a34400000003", SYS_NATIVE_ID_A)); // snapshot of a volume
        snapshots.add(initializeSnapshot(null, "d584a34600000005", SYS_NATIVE_ID_A)); // snapshot of a snapshot
        snapshots.add(initializeSnapshot(null, "83f1770700000000", SYS_NATIVE_ID_B));
        snapshots.add(initializeSnapshot(null, "83f177070000000", SYS_NATIVE_ID_B));  // volume doesn't exist
        task = driver.createVolumeSnapshot(snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("PARTIALLY_FAILED", task.getStatus().toString());
        for (VolumeSnapshot snapshot : snapshots) {
            if (snapshot.getParentId() != "83f177070000000") {
                Assert.assertNotNull(snapshot.getNativeId());
            } else {
                Assert.assertNull(snapshot.getNativeId());
            }
        }
    }

    @Test
    public void testDeleteVolumeSnapshot() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        // null
        List<VolumeSnapshot> snapshots = null;
        DriverTask task = driver.deleteVolumeSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // list of valid snapshot
        snapshots = new LinkedList<>();
        snapshots.add(initializeSnapshot(null, "d584a34000000000", SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, "d584a34300000002", SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, "d584a34400000003", SYS_NATIVE_ID_A)); // snapshot of a volume
        snapshots.add(initializeSnapshot(null, "d584a34600000005", SYS_NATIVE_ID_A)); // snapshot of a snapshot
        snapshots.add(initializeSnapshot(null, "83f1770700000000", SYS_NATIVE_ID_B));
        snapshots.add(initializeSnapshot(null, "83f1770800000001", SYS_NATIVE_ID_B));
        driver.createVolumeSnapshot(snapshots, null);
        task = driver.deleteVolumeSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());

        // some of the snapshot are not existed
        snapshots.add(initializeSnapshot("d584a34700000006", "d584a34300000002", SYS_NATIVE_ID_A));
        // existed snapshot,other snapshot that are deleted earlier not longer exist
        task = driver.deleteVolumeSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("PARTIALLY_FAILED", task.getStatus().toString());
    }

    @Test
    public void testCreateConsistencyGroupSnapshot() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        // null
        List<VolumeSnapshot> snapshots = null;
        VolumeConsistencyGroup cg = null;
        DriverTask task = driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // volumes from same storage system
        snapshots = new LinkedList<>();
        snapshots.add(initializeSnapshot(null, "d584a34000000000", SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, "d584a34300000002", SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, "d584a34400000003", SYS_NATIVE_ID_A)); // snapshot of a volume
        snapshots.add(initializeSnapshot(null, "d584a34600000005", SYS_NATIVE_ID_A)); // snapshot of a snapshot
        cg = new VolumeConsistencyGroup();
        task = driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());
        Assert.assertNotNull(cg.getNativeId());
        for (VolumeSnapshot snapshot : snapshots) {
            Assert.assertNotNull(snapshot.getNativeId());
            Assert.assertNotNull(snapshot.getConsistencyGroup());
        }

        // same storage system, some volumes are not existed
        snapshots = new LinkedList<>();
        snapshots.add(initializeSnapshot(null, "d584a34000000000", SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, "d584a3430000", SYS_NATIVE_ID_A));      // volume that is not existed
        snapshots.add(initializeSnapshot(null, "d584a34400000003", SYS_NATIVE_ID_A));  // snapshot of a volume
        snapshots.add(initializeSnapshot(null, "d584a34600000005", SYS_NATIVE_ID_A));  // snapshot of a snapshot
        cg = new VolumeConsistencyGroup();
        task = driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("PARTIALLY_FAILED", task.getStatus().toString());
        Assert.assertNotNull(cg.getNativeId());
        for (VolumeSnapshot snapshot : snapshots) {
            if (snapshot.getParentId() != "d584a3430000") {
                Assert.assertNotNull(snapshot.getNativeId());
                Assert.assertNotNull(snapshot.getConsistencyGroup());
            } else {
                Assert.assertNull(snapshot.getNativeId());
                Assert.assertNull(snapshot.getConsistencyGroup());
            }
        }

        // volumes from different storage system
        snapshots = new LinkedList<>();
        snapshots.add(initializeSnapshot(null, "83f1770700000000", SYS_NATIVE_ID_B));
        snapshots.add(initializeSnapshot(null, "83f1770800000001", SYS_NATIVE_ID_B));
        snapshots.add(initializeSnapshot(null, "d584a34000000000", SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, "d584a34300000002", SYS_NATIVE_ID_A));
        cg = new VolumeConsistencyGroup();
        task = driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());
        Assert.assertNull(cg.getNativeId());

    }

    @Test
    public void testDeleteConsistencyGroupSnapshot() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);

        // null
        List<VolumeSnapshot> snapshots = null;
        DriverTask task = driver.deleteConsistencyGroupSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // snapshots are in same consistency group (w/o un-existed snapshot)
        snapshots = new LinkedList<>();
        snapshots.add(initializeSnapshot(null, "d584a34000000000", SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, "d584a34300000002", SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, "d584a34400000003", SYS_NATIVE_ID_A)); // snapshot of a volume
        snapshots.add(initializeSnapshot(null, "d584a34600000005", SYS_NATIVE_ID_A)); // snapshot of a snapshot
        VolumeConsistencyGroup cg = new VolumeConsistencyGroup();
        driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        task = driver.deleteConsistencyGroupSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());

        task = driver.deleteConsistencyGroupSnapshot(snapshots); // snapshots not existed
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // snapshots in different consistency group
        List<VolumeSnapshot> snapshotsB = new LinkedList<>();
        snapshotsB.add(initializeSnapshot(null, "83f1770700000000", SYS_NATIVE_ID_B));
        snapshotsB.add(initializeSnapshot(null, "83f1770800000001", SYS_NATIVE_ID_B));
        VolumeConsistencyGroup cgB = new VolumeConsistencyGroup();
        driver.createConsistencyGroupSnapshot(cgB, snapshotsB, null);
        driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        snapshots.addAll(snapshotsB);
        task = driver.deleteConsistencyGroupSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

    }

    public VolumeSnapshot initializeSnapshot(String nativeId, String parentId, String storageSystemId) {
        VolumeSnapshot snapshot = new VolumeSnapshot();
        snapshot.setStorageSystemId(storageSystemId);
        snapshot.setParentId(parentId);
        snapshot.setNativeId(nativeId);
        return snapshot;

    }
}