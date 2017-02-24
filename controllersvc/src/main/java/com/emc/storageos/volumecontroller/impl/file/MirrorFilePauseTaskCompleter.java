package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.MirrorStatus;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class MirrorFilePauseTaskCompleter extends MirrorFileTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(MirrorFilePauseTaskCompleter.class);

    public MirrorFilePauseTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);
            recordMirrorOperation(dbClient, OperationTypeEnum.PAUSE_FILE_MIRROR, status, getId());
        } catch (Exception e) {
            _log.error("Failed updating status MirrorSessionPause {}.", getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected String getFileMirrorStatusForSuccess(FileShare fs) {
        return MirrorStatus.PAUSED.name();
    }
}
