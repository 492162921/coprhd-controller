/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.ipsec;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "ipsec_status")
public class IPsecStatus {

    private boolean isGood;
    private List<IPsecNodeState> nodeStatus;

    @XmlElement(name = "version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    private String version;

    @XmlElement(name = "is_good")
    public boolean getIsGood() {
        return isGood;
    }

    public void setIsGood(boolean isGood) {
        this.isGood = isGood;
    }

    @XmlElement(name = "node_status")
    public List<IPsecNodeState> getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(List<IPsecNodeState> nodeStatus) {
        this.nodeStatus = nodeStatus;
    }
}
