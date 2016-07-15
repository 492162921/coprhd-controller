/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;

import java.net.URI;

@Cf("RemoteReplicationElement")
public class RemoteReplicationElement extends DataObject {

//    public enum ElementType {
//        VOLUME,
//        FILE_SYSTEM
//    }

    // uri of backing data object
    private URI objectUri;
//    private URI storageSystemUri;
//    private ElementType elementType;


    @Name("objectUri")
    public URI getObjectUri() {
        return objectUri;
    }

    public void setObjectUri(URI objectUri) {
        this.objectUri = objectUri;
        setChanged("objectUri");
    }
}
