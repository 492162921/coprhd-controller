/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.Map;

import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.google.common.collect.Maps;

import util.datatable.DataTable;

public class ScheculePoliciesDataTable extends DataTable {

    public ScheculePoliciesDataTable() {
        addColumn("policyName").setRenderFunction("renderLink");
        addColumn("policyType");
        addColumn("appliedAt");
        addColumn("vPools");
        addColumn("projects");
        addColumn("priority");
        addColumn("description");
        sortAll();
        setDefaultSortField("policyName");
    }

    public static class FileProtectionPolicy {
        public String id;
        public String policyName;
        public String policyType;
        public String appliedAt;
        public String vPools;
        public String projects;
        public String priority;
        public String description;

        public FileProtectionPolicy(FilePolicyRestRep policy) {
            id = policy.getId().toString();
            policyName = policy.getName();
            policyType = getTranslatedPolicyType(policy.getType());
            appliedAt = policy.getAppliedAt();
            priority = policy.getPriority();
            description = policy.getDescription();
            setAssignedResFromPolicy(policy);
        }

        private void setAssignedResFromPolicy(FilePolicyRestRep policy) {
            StringBuffer assignRes = new StringBuffer();
            boolean first = true;

            if (policy.getAssignedResources() != null) {
                for (NamedRelatedResourceRep res : policy.getAssignedResources()) {
                    if (first) {
                        assignRes.append(res.getName());
                        first = false;
                    } else {
                        assignRes.append(",").append(res.getName());
                    }
                }
                if (FilePolicyApplyLevel.project.name().equals(policy.getAppliedAt())) {
                    vPools = policy.getVpool().getName();
                    projects = assignRes.toString();

                } else if (FilePolicyApplyLevel.vpool.name().equals(policy.getAppliedAt())) {
                    vPools = assignRes.toString();
                }
            }
        }

        private String getTranslatedPolicyType(String key) {
            Map<String, String> translatedProlicyType = Maps.newLinkedHashMap();
            if (key != null && key.contains("snapshot")) {
                translatedProlicyType.put("file_snapshot", "Snapshot");
            } else {
                translatedProlicyType.put("file_replication", "Replication");
            }
            return translatedProlicyType.get(key);
        }
    }
}
