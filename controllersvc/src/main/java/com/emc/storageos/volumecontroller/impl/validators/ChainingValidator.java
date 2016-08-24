/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.exceptions.DeviceControllerException;
import com.google.common.collect.Lists;

/**
 * Chains multiple {@link Validator} instances with a shared {@link ValidatorLogger}.
 * This class will execute each validation and then check to see if any validation
 * errors occurred, throwing an exception if so.
 */
public class ChainingValidator implements Validator {

    private static final Logger log = LoggerFactory.getLogger(ChainingValidator.class);

    private List<Validator> validators;
    private ValidatorLogger logger;
    private ValidatorConfig config;
    private String type;

    public ChainingValidator(ValidatorLogger logger, ValidatorConfig config, String type) {
        validators = Lists.newArrayList();
        this.logger = logger;
        this.config = config;
        this.type = type;
    }

    public boolean addValidator(Validator validator) {
        return validators.add(validator);
    }

    @Override
    public boolean validate() throws Exception {
        try {
            for (Validator validator : validators) {
                validator.validate();
            }
        } catch (Exception e) {
            log.error("Exception occurred during validation: ", e);
            if (config.validationEnabled()) {
                throw DeviceControllerException.exceptions.unexpectedCondition(e.getMessage());
            }
        }

        if (logger.hasErrors()) {
            if (config.validationEnabled()) {
                DefaultValidator.generateException(type, logger);
            }
        }

        return true;
    }
}
