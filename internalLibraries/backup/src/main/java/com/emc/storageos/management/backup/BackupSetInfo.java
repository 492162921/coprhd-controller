/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.management.backup;

import java.io.Serializable;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupSetInfo implements Serializable {
    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 301077366599522567L;

    private String name;
    private long size = 0;
    private long createTime = 0;
  
    public BackupSetInfo() {
    }

    public String getName() {
        return this.name;
    }
 
    public void setName(String name) {
        this.name = name;
    } 

    public long getSize() {
        return this.size;
    }
  
    public void setSize(long size) {
        this.size = size;
    }

    public long getCreateTime() {
        return this.createTime;
    }

    public void setCreateTime(long time) {
        this.createTime = time;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(BackupSetInfo.class.getSimpleName());
        builder.append("(");
        builder.append("name=").append(name);
        double sizeMb = size * 1.0 / BackupConstants.MEGABYTE;
        builder.append(String.format(", size=%1$.2f MB", sizeMb));
        Format format = new SimpleDateFormat(BackupConstants.DATE_FORMAT);
        builder.append(", createTime=").append(format.format(new Date(createTime)));
        builder.append(")");
        return builder.toString();
    }
}
