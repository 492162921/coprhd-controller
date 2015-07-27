/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.WWNUtility;

/**
 * SCSI initiator in either a Fiber Channel or iSCSI SAN.
 */
@Cf("Initiator")
public class Initiator extends HostInterface {
    
    private String _port;
    private String _node;
    //  to do - This is temporary until initiator service is remove
    private String _hostName;
    //  to do - This is temporary until initiator service is remove
    private String _clusterName;
    
    
    /**
     * Default Constructor. This is the constructor used by the API.
     */
    public Initiator() {
        setIsManualCreation(true);
    }
    
    /**
     * Constructor.
     * 
     * @param protocol The initiator port protocol.
     * @param port The initiator port identifier.
     * @param node The initiator node identifier.
     * @param hostName The FQDN of the initiator host.
     * @param isManualCreation the flag that indicates if the initiator is user or system created
     */
    public Initiator(String protocol, String port, String node, String hostName, boolean isManualCreation) {
        super(null, protocol);
        setInitiatorPort(port);
        setInitiatorNode(node == null ? "" : node);
        setHostName(hostName == null ? "" : hostName);
        setIsManualCreation(isManualCreation);
    }

    /**
     * Constructor supports setting of optional cluster name.
     * 
     * @param protocol The initiator port protocol.
     * @param port The initiator port identifier.
     * @param node The initiator node identifier.
     * @param hostName The FQDN of the initiator host.
     * @param clusterName The FQDN of the cluster.
     * @param isManualCreation the flag that indicates if the initiator is user or system created
     */
    public Initiator(String protocol, String port, String node, String hostName,
        String clusterName, boolean isManualCreation) {
        super(null, protocol);
        setInitiatorPort(port);
        setInitiatorNode(node == null ? "" : node);
        setHostName(hostName == null ? "" : hostName);
        setClusterName(clusterName == null ? "" : clusterName);
        setIsManualCreation(isManualCreation);
     }
    
    /**
     * Getter for the initiator port identifier. For FC, this is the port WWN.
     * For iSCSI, this is port name in IQN or EUI format.
     * 
     * @return The initiator port identifier.
     */
    @Name("iniport")
    @AlternateId("InitiatorPortIndex")
    public String getInitiatorPort() {
        return _port;
    }

    /**
     * Setter for the initiator port identifier.
     * 
     * @param port The initiator port identifier.
     */
    public void setInitiatorPort(String port) {
        _port = EndpointUtility.changeCase(port);
        setChanged("iniport");
    }


    /**
     * Getter for the initiator node identifier. For FC, this is the node WWN.
     * For iSCSI, this field is optional.
     * 
     * @return The initiator node identifier.
     */
    @Name("ininode")
    public String getInitiatorNode() {
        return _node;
    }

    /**
     * Setter for the initiator node identifier.
     * 
     * @param node The initiator node identifier.
     */
    public void setInitiatorNode(String node) {
        _node = EndpointUtility.changeCase(node);
        setChanged("ininode");
    }

    /**
     * Getter for the FQDN of the initiator host.
     * to do - This is temporary until initiator service is remove
     * @return The FQDN of the initiator host.
     */
    @AlternateId("AltIdIndex")    
    @Name("hostname")
    public String getHostName() {
        return _hostName;
    }

    /**
     * Setter for the FQDN of the initiator host.
     * to do - This is temporary until initiator service is remove
     * 
     * @param hostName The FQDN of the initiator host.
     */
    public void setHostName(String hostName) {
        _hostName = hostName;
        setChanged("hostname");
    }
    
    /**
     * Getter for the FQDN of the initiator cluster.
     * to do - This is temporary until initiator service is remove
     * @return The FQDN of the initiator cluster or null if not applicable.
     */
    @Name("clustername")
    public String getClusterName() {
        return _clusterName;
    }

    /**
     * Setter for the FQDN of the initiator cluster.
     * to do - This is temporary until initiator service is remove
     * @param clusterName The FQDN of the initiator cluster.
     */
    public void setClusterName(String clusterName) {
        _clusterName = clusterName;
        setChanged("clustername");
    }
    
    @Override
    public final String toString() {
        return String.format(
            "Initiator(Protocol:%s, Node:%s, Port:%s, Host Name: %s, Cluster Name: %s)",
            getProtocol(), getInitiatorNode(), getInitiatorPort(), getHostName(),
            getClusterName());
    }

    static public String normalizePort(String port) {
        String normalizedPort = port;
        if (WWNUtility.isValidWWN(port)) {
            normalizedPort = WWNUtility.getUpperWWNWithNoColons(port);
        }
        return normalizedPort;
    }

    static public String toPortNetworkId(String port) {
        String portNetworkId = port;
        if (port.startsWith("iqn")) {
            // iSCSI port may have some other values after the port name (e.g.,
            // iqn.1992-04.com.emc:cx.apm00121500018.b9,t,0x0001).
            // Exclude the extraneous parts to arrive at
            // iqn.1992-04.com.emc:cx.apm00121500018.b9
            int firstComma = port.indexOf(',');
            if (firstComma != -1) {
                portNetworkId = port.substring(0, firstComma);
            }
        } else if (!WWNUtility.isValidWWN(port)) {
            portNetworkId = WWNUtility.getWWNWithColons(port);
        }
        return portNetworkId;
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] {getInitiatorPort(), getInitiatorNode(),
                 getHost(), getId()};
    }
}

