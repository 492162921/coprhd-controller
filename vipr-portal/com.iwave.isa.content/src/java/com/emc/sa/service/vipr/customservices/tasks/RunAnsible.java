/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
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

package com.emc.sa.service.vipr.customservices.tasks;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.customservices.CustomServicesConstants;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.uimodels.CustomServicesAnsiblePackage;
import com.emc.storageos.db.client.model.uimodels.CustomServicesAnsiblePrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesScriptResource;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.Primitive.StepType;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

/**
 * Runs CustomServices Primitives - Shell script or Ansible Playbook.
 * It can run Ansible playbook on local node as well as on Remote node
 *
 */
public class RunAnsible extends ViPRExecutionTask<CustomServicesTaskResult> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunAnsible.class);

    private final Step step;
    private final Map<String, List<String>> input;
    private final String orderDir;
    private final long timeout;
    private final Map<String, Object> params;
    private final DbClient dbClient;

    public RunAnsible(final Step step, final Map<String, List<String>> input, final Map<String, Object> params, final DbClient dbClient,
            final String orderDir) {
        this.step = step;
        this.input = input;
        if (step.getAttributes() == null || step.getAttributes().getTimeout() == -1) {
            this.timeout = Exec.DEFAULT_CMD_TIMEOUT;
        } else {
            this.timeout = step.getAttributes().getTimeout();
        }
        this.params = params;
        this.dbClient = dbClient;
        this.orderDir = orderDir;
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {

        ExecutionUtils.currentContext().logInfo("runCustomScript.statusInfo", step.getId());
        final URI scriptid = step.getOperation();

        final StepType type = StepType.fromString(step.getType());

        final Exec.Result result;
        try {
            switch (type) {
                case SHELL_SCRIPT:
                    // get the resource from DB
                    final CustomServicesScriptPrimitive primitive = dbClient.queryObject(CustomServicesScriptPrimitive.class, scriptid);
                    if (null == primitive) {
                        logger.error("Error retrieving the script primitive from DB. {} not found in DB", scriptid);
                        throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed(scriptid + " not found in DB");
                    }

                    final CustomServicesScriptResource script = dbClient.queryObject(CustomServicesScriptResource.class,
                            primitive.getScript());

                    if (null == script) {
                        logger.error("Error retrieving the resource for the script primitive from DB. {} not found in DB",
                                primitive.getScript());

                        throw InternalServerErrorException.internalServerErrors
                                .customServiceExecutionFailed(primitive.getScript() + " not found in DB");
                    }

                    // Currently, the stepId is set to random hash values in the UI. If this changes then we have to change the following to
                    // generate filename with URI from step.getOperation()
                    final String scriptFileName = String.format("%s%s.sh", orderDir, step.getId());

                    final byte[] bytes = Base64.decodeBase64(script.getResource());
                    writeShellScripttoFile(bytes, scriptFileName);

                    final String inputToScript = makeParam(input);
                    logger.debug("input is {}", inputToScript);

                    result = executeCmd(scriptFileName, inputToScript);
                    break;
                case LOCAL_ANSIBLE:
                    final CustomServicesAnsiblePrimitive ansiblePrimitive = dbClient.queryObject(CustomServicesAnsiblePrimitive.class,
                            scriptid);
                    final CustomServicesAnsiblePackage ansiblePackageId = dbClient.queryObject(CustomServicesAnsiblePackage.class,
                            ansiblePrimitive.getArchive());

                    // get the playbook which the user has specified during primitive creation from DB.
                    // The playbook (resolved to the path in the archive) represents the playbook to execute
                    final String playbook = ansiblePrimitive.getPlaybook();

                    // get the archive from AnsiblePackage CF
                    final byte[] ansibleArchive = Base64.decodeBase64(ansiblePackageId.getResource());

                    // uncompress Ansible archive to orderDir
                    uncompressArchive(ansibleArchive);

                    // TODO: Hard coded for testing. The following will be removed after completing COP-27888
                    final String hosts = "/opt/storageos/ansi_logs/hosts";

                    final String user = ExecutionUtils.currentContext().getOrder().getSubmittedByUserId();
                    result = executeLocal(hosts, makeExtraArg(input), String.format("%s%s", orderDir, playbook), user);
                    break;
                case REMOTE_ANSIBLE:
                    result = executeRemoteCmd(makeExtraArg(input));

                    break;
                default:
                    logger.error("Step type:{} not supported", type);

                    throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Unsupported Operation");
            }
        } catch (final Exception e) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }

        ExecutionUtils.currentContext().logInfo("runCustomScript.doneInfo", step.getId());

        if (result == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Script/Ansible execution Failed");
        }

        logger.info("CustomScript Execution result:output{} error{} exitValue:{}", result.getStdOutput(), result.getStdError(),
                result.getExitValue());

        return new CustomServicesTaskResult(parseOut(result.getStdOutput()), result.getStdError(), result.getExitValue(), null);
    }

    private void writeShellScripttoFile(final byte[] bytes, final String scriptFileName){
        try (FileOutputStream fileOuputStream = new FileOutputStream(scriptFileName)) {
            fileOuputStream.write(bytes);
        } catch (final IOException e) {
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Creating Shell Script file failed with exception:" +
                            e.getMessage());
        }
    }

    private void uncompressArchive(final byte[] ansibleArchive) {
        try (final TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new GzipCompressorInputStream(new ByteArrayInputStream(
                        ansibleArchive)))) {
            TarArchiveEntry entry = tarIn.getNextTarEntry();
            while (entry != null) {
                final File curTarget = new File(orderDir, entry.getName());
                if (entry.isDirectory()) {
                    curTarget.mkdirs();
                } else {
                    final File parent = curTarget.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    final OutputStream out = new FileOutputStream(curTarget);
                    IOUtils.copy(tarIn, out);
                    out.close();

                }
                entry = tarIn.getNextTarEntry();
            }
        } catch (final IOException e) {
            throw InternalServerErrorException.internalServerErrors.genericApisvcError("Invalid ansible archive", e);
        }
    }

    private String parseOut(final String out) {
        if (step.getType().equals(StepType.SHELL_SCRIPT.toString())) {
            logger.info("Type is shell script");

            return out;
        }
        final String regexString = Pattern.quote("output_start") + "(?s)(.*?)" + Pattern.quote("output_end");
        final Pattern pattern = Pattern.compile(regexString);
        final Matcher matcher = pattern.matcher(out);

        while (matcher.find()) {
            return matcher.group(1);
        }

        return out;
    }

    // TODO: Hard coded everything for testing. The following will be removed after completing COP-27888
    // During upload of primitive, user will specify if hosts file is already present or not?
    // If already present, then get it from the param. currently the host file is not stored in DB
    // If not present, dynamically create one with the given hostgroups and IpAddress(e.g: webservers, linuxhosts ...etc)
    // If nothing is given by user default to localhost

    private String getHostFile() throws IOException {
        final boolean isHostFilePresent = false;
        String hosts;
        if (isHostFilePresent) {
            hosts = "/opt/storageos/ansi/hosts";
        } else {
            List<String> lines = Arrays.asList("[webservers]", "10.247.66.88");
            Path file = Paths.get("/opt/storageos/ansi/hosts");
            Files.write(file, lines, Charset.forName("UTF-8"));
            hosts = "/opt/storageos/ansi/hosts";
        }

        if (hosts == null || hosts.isEmpty())
            hosts = "localhost,";

        return hosts;
    }

    // Execute Ansible playbook on remote node. Playbook is also in remote node
    private Exec.Result executeRemoteCmd(final String extraVars) {
        final Map<String, CustomServicesWorkflowDocument.InputGroup> inputType = step.getInputGroups();
        if (inputType == null) {
            return null;
        }

        final AnsibleCommandLine cmd = new AnsibleCommandLine(
                getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_BIN,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()),
                getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_PLAYBOOK,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()));
        final String[] cmds = cmd.setSsh(CustomServicesConstants.SHELL_LOCAL_BIN)
                .setUserAndIp(getAnsibleConnAndOptions(CustomServicesConstants.REMOTE_USER,
                        inputType.get(CustomServicesConstants.CONNECTION_DETAILS).getInputGroup()),
                        getAnsibleConnAndOptions(CustomServicesConstants.REMOTE_NODE,
                                inputType.get(CustomServicesConstants.CONNECTION_DETAILS).getInputGroup()))
                .setHostFile(getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_HOST_FILE,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()))
                .setUser(getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_USER,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()))
                .setCommandLine(getAnsibleConnAndOptions(CustomServicesConstants.ANSIBLE_COMMAND_LINE,
                        inputType.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup()))
                .setExtraVars(extraVars)
                .build();

        return Exec.exec(timeout, cmds);
    }

    // Execute Ansible playbook on given nodes. Playbook in local node
    private Exec.Result executeLocal(final String ips, final String extraVars, final String playbook, final String user) {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(CustomServicesConstants.ANSIBLE_LOCAL_BIN, playbook);
        final String[] cmds = cmd.setHostFile(ips).setUser(user)
                .setLimit(null)
                .setTags(null)
                .setExtraVars(extraVars)
                .build();

        return Exec.exec(timeout, cmds);
    }

    // Execute Shel Script resource
    private Exec.Result executeCmd(final String playbook, final String extraVars) {
        final AnsibleCommandLine cmd = new AnsibleCommandLine(CustomServicesConstants.SHELL_BIN, playbook);
        cmd.setShellArgs(extraVars);
        final String[] cmds = cmd.build();
        return Exec.exec(timeout, cmds);
    }

    private String getAnsibleConnAndOptions(final String key, final List<Input> stepInput) {
        if (params.get(key) != null) {
            return StringUtils.strip(params.get(key).toString(), "\"");
        }

        for (final Input in : stepInput) {
            if (in.getName().equals(key)) {
                if (in.getDefaultValue() != null) {
                    return in.getDefaultValue();
                }
            }
        }

        logger.error("Can't find the value for:{}", key);
        return null;
    }

    /**
     * Ansible extra Argument format:
     * --extra_vars "key1=value1 key2=value2"
     *
     * @param input
     * @return
     * @throws Exception
     */
    private String makeExtraArg(final Map<String, List<String>> input) throws Exception {
        if (input == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder("");
        for (Map.Entry<String, List<String>> e : input.entrySet()) {
            // TODO find a better way to fix this
            sb.append(e.getKey()).append("=").append(e.getValue().get(0).replace("\"", "")).append(" ");
        }
        logger.info("extra vars:{}", sb.toString());

        return sb.toString().trim();
    }

    private String makeParam(final Map<String, List<String>> input) throws Exception {
        final StringBuilder sb = new StringBuilder();
        for (List<String> value : input.values()) {
            // TODO find a better way to fix this
            sb.append(value.get(0).replace("\"", "")).append(" ");
        }
        return sb.toString();
    }
}