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

package com.emc.storageos.vnxe.requests;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.LunCreateParam;
import com.emc.storageos.vnxe.models.LunModifyParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class BlockLunRequests extends KHRequests<VNXeLun>{
    private static final Logger _logger = LoggerFactory.getLogger(BlockLunRequests.class);
    private static final String URL_RESOURCE = "/api/instances/storageResource/";
    private static final String URL_CREATE = "/api/types/storageResource/action/createLun";
    private static final String URL_LUNS = "/api/types/lun/instances";
    private static final String URL_LUN = "/api/instances/lun/";
    private static final String URL_LUN_MODIFY_ACTION = "/action/modifyLun";
    
    public BlockLunRequests(KHClient client) {
        super(client);
    }
    
    /**
     * Get all luns in the array
     * @return
     */
    public List<VNXeLun> get() {
        _url = URL_LUNS;
        return getDataForObjects(VNXeLun.class);
    }
    
    /**
     * Get a lun's detail using its lun id.
     * @param lunId
     * @return
     */
    public VNXeLun getLun(String lunId) {
        _url = URL_LUN + lunId;
        return getDataForOneObject(VNXeLun.class);
    }
    
    public List<VNXeLun> getByStorageResourceId(String storageResourceId) {
        _url = URL_LUNS;
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.STORAGE_RESOURCE_FILTER+storageResourceId);
        setQueryParameters(queryParams);
        List<VNXeLun> result = null;
        List<VNXeLun> lunList = getDataForObjects(VNXeLun.class);
        //it should just return 1
        if (lunList!= null && lunList.size()>0) {
            result =lunList;
        } else {
            _logger.info("No lun found using the storage resource id: " +storageResourceId);
        }
        
        return result;
    }
    
    /**
     * get lun with lungroup Id and lun name
     * @param lunGroupId
     * @param lunName
     * @return
     */
    public VNXeLun getByLunGroup(String lunGroupId, String lunName) {
        _url = URL_LUNS;
        String filter = VNXeConstants.STORAGE_RESOURCE_FILTER + lunGroupId;
        setFilter(filter);
        VNXeLun vnxeLun = null;
        List<VNXeLun> luns = getDataForObjects(VNXeLun.class);
        for (VNXeLun lun : luns) {
            String name = lun.getName();
            if (name != null && name.equals(lunName)) {
                vnxeLun = lun;
                break;
            }
        }
        return vnxeLun;
    }
    
    /**
     * get all luns in the lun group
     * @param lunGroupId
     * @return
     */
    public List<VNXeLun> getLunsInLunGroup(String lunGroupId) {
        _url = URL_LUNS;
        setFilter(VNXeConstants.STORAGE_RESOURCE_FILTER+lunGroupId);

        return getDataForObjects(VNXeLun.class);
        
    }
    
    /**
     * Create a standalone lun
     * @param param
     * @return
     */
    public VNXeCommandJob createLun(LunCreateParam param) {
        _url = URL_CREATE;
        
        return postRequestAsync(param);
    }
    
    /**
     * modify lun (export/unexport/expand etc) in async mode
     * @param param
     * @param resourceId
     * @return
     */
    public VNXeCommandJob modifyLunAsync(LunModifyParam param, String resourceId ) {
    	StringBuilder urlBld = new StringBuilder(URL_RESOURCE);
        urlBld.append(resourceId);
        urlBld.append(URL_LUN_MODIFY_ACTION);
        _url = urlBld.toString();
        
        return postRequestAsync(param);
    }
    
    /**
     * modify lun, export/unexport/expand, etc
     * @param param
     * @param resourceId
     * @return
     */
    public VNXeCommandResult modifyLunSync(LunModifyParam param, String resourceId ) {
        StringBuilder urlBld = new StringBuilder(URL_RESOURCE);
        urlBld.append(resourceId);
        urlBld.append(URL_LUN_MODIFY_ACTION);
        _url = urlBld.toString();
        
        VNXeCommandResult result = postRequestSync(param);
        result.setSuccess(true);
        return result;
        
    }
    
}
