/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.file.FileSystemExportParam;

import static com.emc.sa.service.ServiceParams.*;

@Service("DeleteFileSnapshotExport")
public class DeleteFileSnapshotExportService extends ViPRService {
    
    @Param(SNAPSHOT)
    protected URI snapshot;
    
    @Override
    public void execute() throws Exception {
        FileStorageUtils.deactivateSnapshotExport(snapshot, true, null);
    }
}