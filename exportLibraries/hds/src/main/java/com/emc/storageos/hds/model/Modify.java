/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
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
import com.emc.storageos.hds.xmlgen.XMLConstants;

public class Modify {
    
    private String target;
    
    private boolean isWaitOption;
    
    private String option;
    
    public Modify() {}
    
    public Modify(String target) {
        this.target = target;
    }
    
    public Modify(String target, boolean isWaitOption) {
        this.target = target;
        this.isWaitOption = isWaitOption;
    }

    /**
     * @return the target
     */
    public String getTarget() {
        return target;
    }

    /**
     * @param target the target to set
     */
    public void setTarget(String target) {
        this.target = target;
    }
    
    public String getOption() {
		return option;
	}

	public void setOption(String option) {
		this.option = option;
	}

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.target) {
            xmlString.append(HDSConstants.SPACE_STR).append("target=\"")
                    .append(this.target).append("\" ");
        }
        if (this.isWaitOption) {
            xmlString.append("option=\"wait\"");
        }
        if (null != this.option) {
            xmlString.append(HDSConstants.SPACE_STR).append(XMLConstants.SPACE)
                    .append("option=").append(HDSConstants.QUOTATION_STR)
                    .append(this.option).append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }

	

}
