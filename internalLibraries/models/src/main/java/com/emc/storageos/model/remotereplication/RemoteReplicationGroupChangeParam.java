package com.emc.storageos.model.remotereplication;


import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * Remote replication group change parameters
 */
@XmlRootElement(name = "remote_replication_group")
public class RemoteReplicationGroupChangeParam {
    URI remoteReplicationGroup;

    @XmlElement(name = "replication_group")
    public URI getRemoteReplicationGroup() {
        return remoteReplicationGroup;
    }

    public void setRemoteReplicationGroup(URI remoteReplicationGroup) {
        this.remoteReplicationGroup = remoteReplicationGroup;
    }
}
