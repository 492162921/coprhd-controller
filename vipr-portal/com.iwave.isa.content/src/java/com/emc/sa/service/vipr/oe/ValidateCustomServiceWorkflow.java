/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.sa.service.vipr.oe;

import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument.Input;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument.Step;
import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ValidateCustomServiceWorkflow {

    private final Map<String, Object> params;
    private final Map<String, Step> stepsHash;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ValidateCustomServiceWorkflow.class);

    public ValidateCustomServiceWorkflow(Map<String, Object> params, Map<String, Step> stepsHash) {
        this.params = params;
        this.stepsHash = stepsHash;
    }

    public void validate() throws InternalServerErrorException, IOException {

        if(stepsHash.get(Primitive.StepType.START.toString()) == null || stepsHash.get(Primitive.StepType.END.toString()) == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Start or End Step not defined");
        }

        for (final Step step1 : stepsHash.values()) {
        	validateStep(step1);
            logger.info("Validate step input");
            validateStepInput(step1);
        }
    }

    private boolean validateInput(Map<String, OrchestrationWorkflowDocument.InputGroup> input) throws InternalServerErrorException {
        if (input == null) {
            logger.info("No Input is defined");
            return true;
        }
        final OrchestrationWorkflowDocument.InputGroup inputGroup = input.get(OrchestrationServiceConstants.INPUT_PARAMS);
        if (inputGroup == null) {
            logger.info("No input params defined");
            return true;
        }
        final List<Input> listInput = inputGroup.getInputGroup();
        if (listInput == null) {
            logger.info("No input param is defined");
            return true;
        }

        return false;
    }

    private void checkNameAndType(final Input in) throws InternalServerErrorException {
        if (StringUtils.isEmpty(in.getName())) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Input name not defined");
        }
        if (StringUtils.isEmpty(in.getType())) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Input type not defined for:" + in.getName());
        }
    }

    private void validateStepInput(final Step step) throws InternalServerErrorException {
        final Map<String, OrchestrationWorkflowDocument.InputGroup> input = step.getInputGroups();
        if (validateInput(input)) {
            return;
        }
        final List<Input> listInput = input.get(OrchestrationServiceConstants.INPUT_PARAMS).getInputGroup();
        for (final Input in : listInput) {
            checkNameAndType(in);
            switch (OrchestrationServiceConstants.InputType.fromString(in.getType())) {
                case FROM_USER:
                case ASSET_OPTION:
                    validateInputUserParams(in);

                    break;
                case FROM_STEP_INPUT:
                case FROM_STEP_OUTPUT:
                    validateOtherStepParams(in);

                    break;
                default:
                    throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Invalid Input type:" + in.getType());
            }
        }
    }

    private void validateInputUserParams(final Input in) {
        if (params.get(in.getName()) == null && in.getDefaultValue() == null && in.getRequired()) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("input param is required but no value defined for:" + in.getName());
        }
    }

    private void validateOtherStepInput(final Step step, final Input in, final String attribute) throws InternalServerErrorException{
        if (step.getInputGroups() == null
                || step.getInputGroups().get(OrchestrationServiceConstants.INPUT_PARAMS) == null
                || step.getInputGroups().get(OrchestrationServiceConstants.INPUT_PARAMS).getInputGroup() == null)
        {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Other Step Input param not defined for input" + in.getName());
        }

        final List<Input> in1 = step.getInputGroups().get(OrchestrationServiceConstants.INPUT_PARAMS).getInputGroup();

        for (final Input e : in1) {
            if (e.getName().equals(attribute)) {
                return;
            }
        }

        throw InternalServerErrorException.internalServerErrors.
                customServiceExecutionFailed("Cannot find value for input param" + in.getName());
    }

    private void validateOtherStepOutput(final Step step, final Input in, final String attribute) throws InternalServerErrorException {
        if (step.getOutput() == null) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Other Step Output param not defined for input:" + in.getName());
        }
        for (OrchestrationWorkflowDocument.Output out : step.getOutput()) {
            if (out.getName().equals(attribute)) {
                return;
            }
        }

        throw InternalServerErrorException.internalServerErrors.
                customServiceExecutionFailed("Cannot find value for input param" + in.getName());
    }

    private void validateOtherStepParams(final Input in) throws InternalServerErrorException {
        if (StringUtils.isEmpty(in.getValue())) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("input from other step value is not defined");
        }
        final String[] paramVal = in.getValue().split("\\.");
        final String stepId = paramVal[0];
        final String attribute = paramVal[1];
        final Step step1 = stepsHash.get(stepId);
        if (step1 == null) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Step not defined. Cannot get value for:" + in.getName());
        }

        if (in.getType().equals(OrchestrationServiceConstants.InputType.FROM_STEP_INPUT.toString())) {
            validateOtherStepInput(step1, in, attribute);
        } else if (in.getType().equals(OrchestrationServiceConstants.InputType.FROM_STEP_OUTPUT.toString())) {
            validateOtherStepOutput(step1, in, attribute);
        }

        throw InternalServerErrorException.internalServerErrors.
                customServiceExecutionFailed("Cannot find value for input param" + in.getName());
    }

    private void validateStep(final Step step) {
        if (step == null || step.getId() == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Workflow Step is null");
        }
        if (step.getId().equals(Primitive.StepType.END.toString())) {
            return;
        }
        if (step.getNext() == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Next step not defined for step:" + step.getId());
        }
        if (step.getNext().getDefaultStep() == null && step.getNext().getFailedStep() == null) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Next step not defined for step:" + step.getId());
        }
    }
}

