/**
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.scaleio.api.restapi.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.emc.storageos.scaleio.api.ParsePattern;
import com.emc.storageos.scaleio.api.ScaleIOConstants;

/**
 * Tiebreakers attributes
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TieBreakers {
	 private String clusterState;

	    private String id;

	    private String goodNodesNum;

	    private TieBreakers[] tieBreakers;

	    private String name;

	    private Slaves[] slaves;

	    private String goodReplicasNum;

	    private Master master;

	    private String clusterMode;

	    public String getClusterState ()
	    {
	        return clusterState;
	    }

	    public void setClusterState (String clusterState)
	    {
	        this.clusterState = clusterState;
	    }

	    public String getId ()
	    {
	        return id;
	    }

	    public void setId (String id)
	    {
	        this.id = id;
	    }

	    public String getGoodNodesNum ()
	    {
	        return goodNodesNum;
	    }

	    public void setGoodNodesNum (String goodNodesNum)
	    {
	        this.goodNodesNum = goodNodesNum;
	    }

	    public TieBreakers[] getTieBreakers ()
	    {
	        return tieBreakers;
	    }

	    public void setTieBreakers (TieBreakers[] tieBreakers)
	    {
	        this.tieBreakers = tieBreakers;
	    }

	    public String getName ()
	    {
	        return name;
	    }

	    public void setName (String name)
	    {
	        this.name = name;
	    }

	    public Slaves[] getSlaves ()
	    {
	        return slaves;
	    }

	    public void setSlaves (Slaves[] slaves)
	    {
	        this.slaves = slaves;
	    }

	    public String getGoodReplicasNum ()
	    {
	        return goodReplicasNum;
	    }

	    public void setGoodReplicasNum (String goodReplicasNum)
	    {
	        this.goodReplicasNum = goodReplicasNum;
	    }

	    public Master getMaster ()
	    {
	        return master;
	    }

	    public void setMaster (Master master)
	    {
	        this.master = master;
	    }

	    public String getClusterMode ()
	    {
	        return clusterMode;
	    }

	    public void setClusterMode (String clusterMode)
	    {
	        this.clusterMode = clusterMode;
	    }

	    @Override
	    public String toString()
	    {
	        return "ClassPojo [clusterState = "+clusterState+", id = "+id+", goodNodesNum = "+goodNodesNum+", tieBreakers = "+tieBreakers+", name = "+name+", slaves = "+slaves+", goodReplicasNum = "+goodReplicasNum+", master = "+master+", clusterMode = "+clusterMode+"]";
	    }
}
