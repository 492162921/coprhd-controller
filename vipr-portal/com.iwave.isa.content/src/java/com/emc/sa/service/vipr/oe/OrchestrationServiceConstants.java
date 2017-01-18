/*
 * Copyright 2016
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

import java.util.Arrays;
import java.util.List;

/**
 * Orchestration Engine Constants
 */
public final class OrchestrationServiceConstants {


    public static final int STEP_ID = 0;
    public static final int INPUT_FIELD = 1;
    public static final String WF_ID = "WorkflowId";

    //SuccessCriteria Constants
    public static final String RETURN_CODE = "code";
    public static final String TASK = "task";

    public static final List<String> BODY_REST_METHOD = Arrays.asList("POST", "PUT", "DELETE");

    public enum restMethods {
        GET, POST, PUT, DELETE;
    }


    //Ansible Execution Constants
    public static final String ANSIBLE_LOCAL_BIN = "/usr/bin/ansible-playbook";
    public static final String SHELL_BIN = "/usr/bin/sh";
    public static final String SHELL_LOCAL_BIN = "/usr/bin/ssh";
    public static final String PATH = "/opt/storageos/";
    public static final String EXTRA_VARS = "--extra-vars";
    public static final String UNTAR = "tar";
    public static final String UNTAR_OPTION = "-zxvf";
    public static final String REMOVE = "/bin/rm";
    public static final String REMOVE_OPTION = "-rf";

    //Ansible Options
    public static final String ANSIBLE_BIN = "remote_ansible_bin";
    public static final String ANSIBLE_PLAYBOOK = "remote_node_playbook";
    public static final String ANSIBLE_HOST_FILE = "remote_host_file";
    public static final String ANSIBLE_USER = "remote_ansible_user";
    public static final String ANSIBLE_COMMAND_LINE = "ansible_command_line_arg";

    //Remote ansible options
    public static final String REMOTE_USER = "remote_node_user";
    public static final String REMOTE_NODE= "remote_node_ip";

    public enum InputType {
        FROM_USER("InputFromUser"),
        FROM_STEP_INPUT("FromOtherStepInput"),
        FROM_STEP_OUTPUT("FromOtherStepOutput"),
        OTHERS("Others"),
        ASSET_OPTION("AssetOption");

        private final String inputType;
        private InputType(final String inputType)
        {
            this.inputType = inputType;
        }

        @Override
        public String toString() {
            return inputType;
        }

       public static InputType fromString(String v)
       {
            for (InputType e : InputType.values())
            {
                if (v.equals(e.inputType)) 
                    return e;
            }

            return null;
        }

    }
}
