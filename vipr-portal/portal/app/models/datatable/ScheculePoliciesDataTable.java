/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.Map;

import util.datatable.DataTable;

import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyRestRep;
import com.google.common.collect.Maps;

public class ScheculePoliciesDataTable extends DataTable{
    
    public ScheculePoliciesDataTable() {
        addColumn("policyName").setRenderFunction("renderLink");
        addColumn("policyType");
        sortAll();
        setDefaultSortField("policyName");
    }

    public static class ScheculePolicy {
        public String id;
        public String policyName;
        public String policyType;
        
        
        public ScheculePolicy(SchedulePolicyRestRep policy) {
            id = policy.getPolicyId().toString();
            policyName = policy.getPolicyName();
            policyType = getTranslatedPolicyType(policy.getPolicyType());
            
        }
        
        private String getTranslatedPolicyType(String key){
            Map<String,String> translatedProlicyType = Maps.newLinkedHashMap();
            translatedProlicyType.put("file_snapshot", "File Snapshot");
            return translatedProlicyType.get(key);
        }
    }
    
    public static class FileProtectionPolicy {
        public String id;
        public String policyName;
        public String policyType;
        
        
        public FileProtectionPolicy(FilePolicyRestRep policy) {
            id = policy.getId().toString();
            policyName = policy.getName();
            policyType = getTranslatedPolicyType(policy.getType());
            
        }
        
        private String getTranslatedPolicyType(String key){
            Map<String,String> translatedProlicyType = Maps.newLinkedHashMap();
            if(key.contains("snapshot")){
            translatedProlicyType.put("file_snapshot", "File Snapshot");
            } else {
            	translatedProlicyType.put("file_replication", "File Replication");
            }
            return translatedProlicyType.get(key);
        }
    }

}
