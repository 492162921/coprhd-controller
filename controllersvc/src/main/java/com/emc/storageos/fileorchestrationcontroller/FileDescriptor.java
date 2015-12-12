/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

import java.io.Serializable;
import java.net.URI;
import java.util.*;


public class FileDescriptor implements Serializable {

    public FileDescriptor(Type _type, URI _deviceURI, URI _fsURI, URI _poolURI,
			Long _fileSize,
			VirtualPoolCapabilityValuesWrapper _capabilitiesValues,
			URI _migrationId, String _suggestedNativeFsId) {
		super();
		this._type = _type;
		this._deviceURI = _deviceURI;
		this._fsURI = _fsURI;
		this._poolURI = _poolURI;
		this._fileSize = _fileSize;
		this._capabilitiesValues = _capabilitiesValues;
		this._migrationId = _migrationId;
		this._suggestedNativeFsId = _suggestedNativeFsId;
		
	}
    
    public FileDescriptor(Type _type, URI _deviceURI, URI _fsURI, URI _poolURI,
						String deletionType, boolean forceDelete) {
		super();

		this._type = _type;
		this._deviceURI = _deviceURI;
		this._fsURI = _fsURI;
		this._poolURI = _poolURI;
		this.deleteType = deletionType;
		this.forceDelete = forceDelete;
	}
    
	public enum Type {
        /* ******************************
         * The ordering of these are important for the sortByType() method,
         * be mindful when adding/removing/changing the list.
         * Especially the RP Values, keep them in sequential order.
         * ******************************
         */
        FILE_DATA(1),               // user's data filesystem
        FILE_MIRROR(2),             // array level mirror
        FILE_SNAPSHOT(3),           // array level snapshot
        FILE_EXISTING_SOURCE(4), // RecoverPoint existing source file
        FILE_RP_SOURCE(5),          // RecoverPoint source
        FILE_RP_TARGET(6);          // RecoverPoint target

        private final int order;
        private Type(int order) {
            this.order = order;
        }
        public int getOrder() {
            return order;
        }
    };
    
    public enum DeleteType{
    	FULL,
    	VIPR_ONLY
    }

    private Type _type;              // The type of this file
    private URI _deviceURI;          // Device this file will be created on
    private URI _fsURI;              // The file id or FileObject id to be created
    private URI _poolURI;            // The pool id to be used for creation
    private Long _fileSize;          // Used to separate multi-file create requests
    private VirtualPoolCapabilityValuesWrapper _capabilitiesValues;  // Non-file-specific RP policy is stored in here
    private URI _migrationId;        // Reference to the migration object for this file
    private String _suggestedNativeFsId;
    private String deleteType;
    
    public String getDeleteType() {
		return deleteType;
	}

	public void setDeleteType(String deleteType) {
		this.deleteType = deleteType;
	}

	public boolean isForceDelete() {
		return forceDelete;
	}

	public void setForceDelete(boolean forceDelete) {
		this.forceDelete = forceDelete;
	}

	private boolean forceDelete;

    public String getSuggestedNativeFsId() {
		return _suggestedNativeFsId;
	}

	public void setSuggestedNativeFsId(String _suggestedNativeFsId) {
		this._suggestedNativeFsId = _suggestedNativeFsId;
	}

	public Type getType() {
        return _type;
    }

    public void setType(Type _type) {
        this._type = _type;
    }

    public URI getDeviceURI() {
        return _deviceURI;
    }

    public void setDeviceURI(URI _deviceURI) {
        this._deviceURI = _deviceURI;
    }

    public URI getFsURI() {
        return _fsURI;
    }

    public void setFsURI(URI _fsURI) {
        this._fsURI = _fsURI;
    }

    public URI getPoolURI() {
        return _poolURI;
    }

    public void setPoolURI(URI _poolURI) {
        this._poolURI = _poolURI;
    }

    public VirtualPoolCapabilityValuesWrapper getCapabilitiesValues() {
        return _capabilitiesValues;
    }

    public void setCapabilitiesValues(VirtualPoolCapabilityValuesWrapper _capabilitiesValues) {
        this._capabilitiesValues = _capabilitiesValues;
    }

    public Long getFileSize() {
        return _fileSize;
    }

    public void setFileSize(Long _fileSize) {
        this._fileSize = _fileSize;
    }

    public URI getMigrationId() {
        return _migrationId;
    }

    public void setMigrationId(URI _migrationId) {
        this._migrationId = _migrationId;
    }

    /**
     * Sorts the descriptors using the natural order of the enum type
     * defined at the top of the class.
     *
     * @param descriptors FileDescriptors to sort
     */
    public static void sortByType(List<FileDescriptor> descriptors) {
        Collections.sort(descriptors, new Comparator<FileDescriptor>() {
            @Override
            public int compare(FileDescriptor vd1, FileDescriptor vd2) {
                return vd1.getType().getOrder() - vd2.getType().getOrder();
            }
        });
    }
    /**
     * Return a map of device URI to a list of descriptors in that device.
     *
     * @param descriptors List<FileDescriptors>
     * @return Map of device URI to List<FileDescriptors> in that device
     */
    static public Map<URI, List<FileDescriptor>> getDeviceMap(List<FileDescriptor> descriptors) {
        HashMap<URI, List<FileDescriptor>> poolMap = new HashMap<URI, List<FileDescriptor>>();
        for (FileDescriptor desc : descriptors) {
            if (poolMap.get(desc._deviceURI) == null) {
                poolMap.put(desc._deviceURI, new ArrayList<FileDescriptor>());
            }
            poolMap.get(desc._deviceURI).add(desc);
        }
        return poolMap;
    }

    /**
     * Return a map of pool URI to a list of descriptors in that pool.
     *
     * @param descriptors List<FileDescriptors>
     * @return Map of pool URI to List<FileDescriptors> in that pool
     */
    static public Map<URI, List<FileDescriptor>> getPoolMap(List<FileDescriptor> descriptors) {
        HashMap<URI, List<FileDescriptor>> poolMap = new HashMap<URI, List<FileDescriptor>>();
        for (FileDescriptor desc : descriptors) {
            if (poolMap.get(desc._poolURI) == null) {
                poolMap.put(desc._poolURI, new ArrayList<FileDescriptor>());
            }
            poolMap.get(desc._poolURI).add(desc);
        }
        return poolMap;
    }
    
    

    
    /**
     * Returns all the descriptors of a given type.
     * 
     * @param descriptors List<FileDescriptor> input list
     * @param type enum Type
     * @return returns list elements matching given type
     */
    static public List<FileDescriptor> getDescriptors(List<FileDescriptor> descriptors, Type type) {
        List<FileDescriptor> list = new ArrayList<FileDescriptor>();
        for (FileDescriptor descriptor : descriptors) {
            if (descriptor._type == type) {
                list.add(descriptor);
            }
        }
        return list;
    }

    /**
     * Return a map of pool URI to a list of descriptors in that pool of each size.
     *
     * @param descriptors List<FileDescriptors>
     * @return Map of pool URI to a map of identical sized filesystems to List<FileDescriptors> in that pool of that size
     */
    static public Map<URI, Map<Long, List<FileDescriptor>>> getPoolSizeMap(List<FileDescriptor> descriptors) {
        Map<URI, Map<Long, List<FileDescriptor>>> poolSizeMap = new HashMap<URI, Map<Long, List<FileDescriptor>>>();
        for (FileDescriptor desc : descriptors) {

            // If the outside pool map doesn't exist, create it.
            if (poolSizeMap.get(desc._poolURI) == null) {
                poolSizeMap.put(desc._poolURI, new HashMap<Long, List<FileDescriptor>>());
            }

            // If the inside size map doesn't exist, create it.
            if (poolSizeMap.get(desc._poolURI).get(desc.getFileSize()) == null) {
                poolSizeMap.get(desc._poolURI).put(desc.getFileSize(), new ArrayList<FileDescriptor>());
            }

            // Add file to the list
            poolSizeMap.get(desc._poolURI).get(desc.getFileSize()).add(desc);
        }

        return poolSizeMap;
    }

    /**
     * Return a List of URIs for the filesystems.
     *
     * @param descriptors List<FileDescriptors>
     * @return List<URI> of filesystems in the input list
     */
    public static List<URI> getFileSystemURIs(List<FileDescriptor> descriptors) {
        List<URI> fileURIs = new ArrayList<URI>();
        for (FileDescriptor desc : descriptors) {
            fileURIs.add(desc._fsURI);
        }
        return fileURIs;
    }

    /**
     * Filter a list of FileDescriptors by type(s).
     *
     * @param descriptors -- Original list.
     * @param inclusive -- Types to be included (or null if not used).
     * @param exclusive -- Types to be excluded (or null if not used).
     * @return List<FileDescriptor>
     */
    public static List<FileDescriptor> filterByType(
            List<FileDescriptor> descriptors,
            Type[] inclusive, Type[] exclusive) {
        List<FileDescriptor> result = new ArrayList<FileDescriptor>();
        if (descriptors == null) {
            return result;
        }

        HashSet<Type> included = new HashSet<Type>();
        if (inclusive != null) {
            included.addAll(Arrays.asList(inclusive));
        }
        HashSet<Type> excluded = new HashSet<Type>();
        if (exclusive != null) {
            excluded.addAll(Arrays.asList(exclusive));
        }
        for (FileDescriptor desc : descriptors) {
            if (excluded.contains(desc._type)) {
                continue;
            }
            if (included.isEmpty() || included.contains(desc._type)) {
                result.add(desc);
            }
        }
        return result;
    }

    public static List<FileDescriptor> filterByType(
            List<FileDescriptor> descriptors,
            Type... inclusive) {
        return filterByType(descriptors, inclusive, null);
    }

}
