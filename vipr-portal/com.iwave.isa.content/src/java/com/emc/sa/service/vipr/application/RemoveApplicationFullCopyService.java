/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;

@Service("RemoveApplicationFullCopy")
public class RemoveApplicationFullCopyService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    protected String name;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Override
    public void execute() throws Exception {

        List<URI> fullCopyIds = BlockStorageUtils.getSingleFullCopyPerSubGroupAndStorageSystem(applicationId, name,
                subGroups);

        List<URI> allFullCopyIds = BlockStorageUtils.getAllFullCopyVolumes(applicationId, name, subGroups);

        BlockStorageUtils.detachFullCopies(fullCopyIds);

        BlockStorageUtils.removeBlockResources(allFullCopyIds, VolumeDeleteTypeEnum.FULL);
    }
}
