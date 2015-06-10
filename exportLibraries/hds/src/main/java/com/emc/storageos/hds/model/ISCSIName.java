/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class ISCSIName {
    
    private String iSCSIName;
    
    private String nickName;
    
    public ISCSIName (String iSCSIName, String nickName) {
        this.iSCSIName = iSCSIName;
        this.nickName = nickName;
    }

    public ISCSIName() { }

    /**
     * @return the iSCSIName
     */
    public String getiSCSIName() {
        return iSCSIName;
    }

    /**
     * @param iSCSIName the iSCSIName to set
     */
    public void setiSCSIName(String iSCSIName) {
        this.iSCSIName = iSCSIName;
    }

    /**
     * @return the nickName
     */
    public String getNickName() {
        return nickName;
    }

    /**
     * @param nickName the nickName to set
     */
    public void setNickName(String nickName) {
        this.nickName = nickName;
    }
    
    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != iSCSIName) {
            xmlString.append(HDSConstants.SPACE_STR).append("iSCSIName=")
                    .append(HDSConstants.QUOTATION_STR).append(this.iSCSIName)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != nickName) {
            xmlString.append(HDSConstants.SPACE_STR).append("nickName=")
                    .append(HDSConstants.QUOTATION_STR).append(this.nickName)
                    .append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }
}
