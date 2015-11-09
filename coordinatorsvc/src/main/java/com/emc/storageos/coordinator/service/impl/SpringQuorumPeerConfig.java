/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.service.impl;


import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import com.emc.storageos.coordinator.client.model.Constants;

import org.apache.zookeeper.server.quorum.QuorumPeer.LearnerType;
import org.apache.zookeeper.server.quorum.QuorumPeer.QuorumServer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig.ConfigException;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.services.util.FileUtils;

public class SpringQuorumPeerConfig extends QuorumPeerConfig {
    private static final Logger log = LoggerFactory.getLogger(SpringQuorumPeerConfig.class);
    private static final String SERVER_ID_FILE = "myid";

    private int _id;
    private Properties _properties;

    public void setMachineId(int id) {
        _id = id;
    }

    public void setServerId(int id) {
        this.serverId = id;
    }
    
    public void setProperties(Properties properties) {
        _properties = properties;
    }

    public Properties getProperties() {
        return _properties;
    }
    
    public int getNumberOfObservers() {
        return observers.size();
    }
    
    public int getNumberOfParitipants() {
        return servers.size() - observers.size();
    }
    
    public void init() throws ConfigException, IOException {
        String dataDir = (String) _properties.get("dataDir");

        //create zk data directory if it doesn't exist
        File serverIdDir = new File(dataDir);
        if (!serverIdDir.exists()) {
            if (!serverIdDir.mkdirs()) {
                throw CoordinatorException.fatals
                        .unableToCreateServerIDDirectories(serverIdDir.getAbsolutePath());
            }
        }

        //create zk server id file if it doesn't exist or not match current server id
        File serverId = new File(serverIdDir, SERVER_ID_FILE);
        boolean shouldRecreate = true;
        if (serverId.exists()) {
            String s = new String(FileUtils.readDataFromFile(serverId.getAbsolutePath()));
            int currentId = Integer.parseInt(s);
            if (currentId == _id) {
                shouldRecreate = false;
            }
         }
        if (shouldRecreate) {
            FileWriter writer = new FileWriter(serverId);
            writer.write(Integer.toString(_id));
            writer.close();
        }
        
        // Pre-process the properties to parse and remove server.* key/value
        // In future, if _properties is still used somewhere else after init, we need to clone it keep original values
        preprocessQuorumServers(_properties);
        parseProperties(_properties);
    }
    
    protected void preprocessQuorumServers(Properties zkProp) throws ConfigException {
        log.info("Preprocess quorum server from properties before send to ZK");

        Iterator<Entry<Object, Object>> iterator = _properties.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Object, Object> entry = iterator.next();
            String key = entry.getKey().toString().trim();
            if (key.startsWith("server.")) {
                String value = entry.getValue().toString().trim();
                createQuorumServer(key, value);
                iterator.remove();
            }
        }
    }

    /**
     * This logic is same as ZK 3.4.6 except splitting values with "," instead of ":"
     */
    private void createQuorumServer(String key, String value) throws ConfigException {
        int dot = key.indexOf('.');
        long sid = Long.parseLong(key.substring(dot + 1));
        String parts[] = value.split(",");
        if ((parts.length != 2) && (parts.length != 3) && (parts.length != 4)) {
            log.error(value + " does not have the form host,port or host,port,port " + " or host,port,port,type");
        }
        InetSocketAddress addr = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
        if (parts.length == 2) {
            servers.put(Long.valueOf(sid), new QuorumServer(sid, addr));
        } else if (parts.length == 3) {
            InetSocketAddress electionAddr = new InetSocketAddress(parts[0], Integer.parseInt(parts[2]));
            servers.put(Long.valueOf(sid), new QuorumServer(sid, addr, electionAddr));
        } else if (parts.length == 4) {
            InetSocketAddress electionAddr = new InetSocketAddress(parts[0], Integer.parseInt(parts[2]));
            LearnerType type = LearnerType.PARTICIPANT;
            if (parts[3].toLowerCase().equals("observer")) {
                type = LearnerType.OBSERVER;
                observers.put(Long.valueOf(sid), new QuorumServer(sid, addr, electionAddr, type));
            } else if (parts[3].toLowerCase().equals("participant")) {
                type = LearnerType.PARTICIPANT;
                servers.put(Long.valueOf(sid), new QuorumServer(sid, addr, electionAddr, type));
            } else {
                throw new ConfigException("Unrecognised peertype: " + value);
            }
        }
    }
    
    public SpringQuorumPeerConfig createNewConfig(Properties newProp, int serverId) throws IOException, ConfigException {
        SpringQuorumPeerConfig newConfig = new SpringQuorumPeerConfig();
        newConfig.setMachineId(serverId);
        Properties prop = new Properties(); 
        prop.putAll(getProperties());
        prop.putAll(newProp);
        newConfig.setProperties(prop);
        newConfig.servers.putAll(servers);
        newConfig.init();
        return newConfig;
    }
}
