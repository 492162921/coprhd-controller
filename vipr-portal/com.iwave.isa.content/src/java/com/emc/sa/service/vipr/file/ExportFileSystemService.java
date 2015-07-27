/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.COMMENT;
import static com.emc.sa.service.ServiceParams.FILESYSTEMS;
import static com.emc.sa.service.ServiceParams.SUBDIRECTORY;
import static com.emc.sa.service.vipr.file.FileConstants.DEFAULT_ROOT_USER;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.file.ExportRule;


@Service("ExportFileSystem")
public class ExportFileSystemService extends ViPRService {
    
    @Param(FILESYSTEMS)
    protected URI fileSystems;
    @Param(COMMENT)
    protected String comment;
    @Param(SUBDIRECTORY)
    protected String subDirectory;
    
    @Bindable(itemType = FileStorageUtils.FileExportRule.class)
    protected FileStorageUtils.FileExportRule[] exportRules;
    
    @Override
    public void precheck() throws Exception{
        if (exportRules == null || exportRules.length == 0) {
            ExecutionUtils.fail("failTask.CreateFileSystemExport.precheck", new Object[]{}, new Object[]{});
        }
    }
    
    @Override
    public void execute() throws Exception {
        if (exportRules != null) {
            String exportId = FileStorageUtils.createFileSystemExport(fileSystems, comment, exportRules[0], subDirectory);
            if (exportRules.length > 1 && StringUtils.isNotBlank(exportId)) {
                FileStorageUtils.updateFileSystemExport(fileSystems, subDirectory, exportRules);
            }
        }
    }
}