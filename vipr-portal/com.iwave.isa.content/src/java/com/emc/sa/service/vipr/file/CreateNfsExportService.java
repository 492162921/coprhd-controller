/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.*;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("NasNfsCreateStorage")
public class CreateNfsExportService extends ViPRService {

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;
    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;
    @Param(PROJECT)
    protected URI project;
    @Param(COMMENT)
    protected String comment;
    @Param(SIZE_IN_GB)
    protected Double sizeInGb;
    @Param(VOLUME_NAME)
    protected String exportName;
    @Param(SOFT_LIMIT)
    protected Integer softLimit;
    @Param(ADIVSORY_LIMIT)
    protected Integer advisoryLimit;
    @Param(GRACE_PERIOD)
    protected Integer gracePeriod;
    
    @Bindable(itemType = FileStorageUtils.FileExportRule.class)
    protected FileStorageUtils.FileExportRule[] exportRules;

    @Override
    public void precheck() throws Exception {
        if (exportRules == null || exportRules.length == 0) {
            ExecutionUtils.fail("failTask.CreateFileSystemExport.precheck", new Object[] {}, new Object[] {});
        }
    }

    @Override
    public void execute() {
        URI fileSystemId = FileStorageUtils.createFileSystem(project, virtualArray, virtualPool, exportName, sizeInGb, softLimit, advisoryLimit, gracePeriod);
        if (exportRules != null) {
            FileStorageUtils.createFileSystemExport(fileSystemId, comment, exportRules[0], null);
            if (exportRules.length > 1) {
                FileStorageUtils.updateFileSystemExport(fileSystemId, null, exportRules);
            }
        }
    }
}
