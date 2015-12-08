/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

@SuppressWarnings({ "squid:S00100" })
/*
 * Isilon API return with json fields has underline.
 */
public class IsilonStoragePool {

    // [{"disk_usage":{"available":15754415955968,"total":16921439059968,"used":94310457344},"entry_id":1,"name":"x200_5.5tb_200gb-ssd_6gb"}]
	/*
	{
	"storagepools" : 
	[
	
	{
	"children" : [],
	"id" : 5,
	"lnns" : [],
	"name" : "test",
	"type" : "tier",
	"usage" : 
	{
	"avail_bytes" : "0",
	"avail_ssd_bytes" : "0",
	"balanced" : true,
	"free_bytes" : "0",
	"free_ssd_bytes" : "0",
	"total_bytes" : "0",
	"total_ssd_bytes" : "0",
	"virtual_hot_spare_bytes" : "0"
	}
	},
	
	{
	"can_disable_l3" : true,
	"can_enable_l3" : true,
	"id" : 1,
	"l3" : false,
	"l3_status" : "storage",
	"lnns" : [ 1, 3 ],
	"manual" : false,
	"name" : "x200_5.5tb_200gb-ssd_6gb",
	"protection_policy" : "+2d:1n",
	"type" : "nodepool",
	"usage" : 
	{
	"avail_bytes" : "9709328130048",
	"avail_ssd_bytes" : "394471268352",
	"balanced" : false,
	"free_bytes" : "10769470152704",
	"free_ssd_bytes" : "394471268352",
	"total_bytes" : "10786122113024",
	"total_ssd_bytes" : "394539646976",
	"virtual_hot_spare_bytes" : "1060142022656"
	}
	}
	],
	"total" : 2
	}

	 */

    private Usage usage;
    private String id;
    private String name;

    public class Usage {
        public String avail_bytes;
        public String total_bytes;
        public String free_bytes;

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[ avail_bytes: " + avail_bytes);
            str.append(", total_bytes: " + total_bytes);
            str.append(", free_bytes: " + free_bytes + "]");
            return str.toString();
        }

    };

    public Usage getUsage() {
        return usage;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAvailableBytes() {
        return getUsage().avail_bytes;
    }

    public String getFreeBytes() {
        return getUsage().free_bytes;
    }

    public String getTotalBytes() {
        return getUsage().total_bytes;
    }

    public String getNativeId() {
        return getName();
    }

	@Override
	public String toString() {
		return "IsilonStoragePool [usage: " + usage + ", id: " + id + ", name: "
				+ name + "]";
	}
}
