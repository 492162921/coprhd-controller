package com.emc.storageos.volumecontroller.impl.validators.vplex;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

/**
 * Factory for creating Vplex-specific validator instances.
 */
public class VplexSystemValidatorFactory implements StorageSystemValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(VplexSystemValidatorFactory.class);
    private DbClient dbClient;

    private List<Volume> remediatedVolumes = Lists.newArrayList();
    private VPlexApiClient client;
    private ValidatorLogger logger;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    
    /**
     * Verify storage system connectivity
     * @param storageSystem
     */
    private void checkVplexConnectivity(StorageSystem storageSystem) {
        try {
            client = VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), storageSystem, dbClient);
        } catch (URISyntaxException ex) {
            log.error("Could connect to VPLEX: " + storageSystem.getLabel(), ex);
        } catch (Exception ex) {
            log.error("Could connect to VPLEX: " + storageSystem.getLabel(), ex);
            throw ex;
        }
    }

    @Override
    public Validator exportMaskDelete(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList, Collection<Initiator> initiatorList) {
        checkVplexConnectivity(storage);
        logger = new ValidatorLogger(log);
        VplexExportMaskValidator validator = new VplexExportMaskValidator(dbClient, logger, storage, exportMask);
        validator.setVolumesToValidate(volumeURIList);
        validator.setInitiatorsToValidate(initiatorList);
        return validator;
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        checkVplexConnectivity(storage);
        logger = new ValidatorLogger(log);
        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        VplexExportMaskValidator validator = new VplexExportMaskValidator(dbClient, logger, storage, exportMask);
        validator.setInitiatorsToValidate(initiators);
        return validator;
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        checkVplexConnectivity(storage);
        logger = new ValidatorLogger(log);
        VplexExportMaskValidator validator = new VplexExportMaskValidator(dbClient, logger, storage, exportMask);
        validator.setVolumesToValidate(volumeURIList);
        return validator;
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
                                ValCk[] checks) {
        checkVplexConnectivity(storageSystem);
        try {
            logger = new ValidatorLogger(log);
            VplexVolumeValidator vplexVolumeValidator = new VplexVolumeValidator(dbClient, logger);
            vplexVolumeValidator.validateVolumes(storageSystem, volumes, delete, remediate, checks);
            if (logger.hasErrors()) {
                throw DeviceControllerException.exceptions.validationError("vplex volume(s)", 
                        logger.getMsgs().toString(), ValidatorLogger.INVENTORY_DELETE_VOLUME);
            }
        } catch (Exception ex) {
            log.error("Unexpected exception validating VPLEX: " + storageSystem.getId(), ex);
            throw ex;
        }
        return remediatedVolumes;
    }
}
