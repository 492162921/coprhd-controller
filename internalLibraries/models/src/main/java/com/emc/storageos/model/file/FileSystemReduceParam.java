/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "filesystem_reduce")
public class FileSystemReduceParam {
	
	private String newSize;

	public String getNewSize() {
		return newSize;
	}

	public void setNewSize(String newSize) {
		this.newSize = newSize;
	}
	
	/**
     * Defines new expanded size of a FileSystem.
     * Supported size formats: TB, GB, MB, B. Default format is size in bytes.
     * Examples: 100GB, 614400000, 614400000B
     * 
     */
	public FileSystemReduceParam(String newSize) {
		super();
		this.newSize = newSize;
	}

	public FileSystemReduceParam() {
		
	}

}
