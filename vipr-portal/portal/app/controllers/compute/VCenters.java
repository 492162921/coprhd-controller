/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.compute;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.host.vcenter.*;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.google.common.collect.Sets;

import controllers.security.Security;
import jobs.vipr.TenantsCall;
import models.datatable.VCenterDataTable;
import models.datatable.VCenterDataTable.VCenterInfo;

import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.Min;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.MessagesUtils;
import util.StringOption;
import util.TenantUtils;
import util.VCenterUtils;
import util.VcenterDataCenterUtils;
import util.builders.ACLUpdateBuilder;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;

import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN"), @Restrict("SECURITY_ADMIN"),
        @Restrict("SYSTEM_ADMIN") })
public class VCenters extends ViprResourceController {

    protected static final String SAVED = "VCenters.saved";
    protected static final String DELETED = "VCenters.deleted";
    protected static final String UNKNOWN = "VCenters.unknown";
    protected static final String MODEL_NAME = "VCenter";
    protected static final String DETACH_STORAGE = "VCenters.detachStorage";

    public static void list() {
        renderArgs.put("dataTable", new VCenterDataTable());
        TenantSelector.addRenderArgsForVcenterObjects();
        renderTenantOptions();
        render();
    }

    public static void listJson() {
        URI tenantId = TenantUtils.getTenantFilter(Models.currentAdminTenantForVcenter());
        List<VcenterRestRep> vcenters = VCenterUtils.getVCenters(tenantId);
        List<VCenterInfo> vcenterInfos = Lists.newArrayList();
        for (VcenterRestRep vcenter : vcenters) {
            vcenterInfos.add(new VCenterInfo(vcenter));
        }
        renderJSON(DataTablesSupport.createJSON(vcenterInfos, params));
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<VCenterInfo> results = Lists.newArrayList();
        if ((ids != null) && (ids.length > 0)) {
            for (String id : ids) {
                if (StringUtils.isBlank(id)) {
                    continue;
                }
                VcenterRestRep vcenter = VCenterUtils.getVCenter(uri(id));
                if (vcenter != null) {
                    results.add(new VCenterInfo(vcenter));
                }
            }
        }
        renderJSON(results);
    }

    public static void itemDetails(String id) {
        VcenterRestRep vcenter = VCenterUtils.getVCenter(uri(id));
        List<VcenterDataCenterRestRep> dataCenters = VCenterUtils.getDataCentersInVCenter(vcenter,
                TenantUtils.getTenantFilter(Models.currentAdminTenantForVcenter()));
        render(vcenter, dataCenters);
    }

    public static void create() {
        VCenterForm vCenter = new VCenterForm();
        vCenter.setTenantsForCreation();

        renderTenantOptions();
        render("@edit", vCenter);
    }

    private static void renderTenantOptions() {
        if (TenantUtils.canReadAllTenantsForVcenters() && VCenterUtils.canUpdateVcenterACLs()) {
            List<StringOption> tenantOptions = dataObjectOptions(await(new TenantsCall().asPromise()));
            renderArgs.put("tenantOptions", tenantOptions);

            List<StringOption> tenantOptionsWithNone = new ArrayList<StringOption>();

            tenantOptionsWithNone.add(new StringOption(NullColumnValueGetter.getNullStr().toString(), "None"));
            tenantOptionsWithNone.addAll(tenantOptions);
            renderArgs.put("tenantOptionsWithNone", tenantOptionsWithNone);
        }
    }

    public static void getVcenterTenantOptions(String id) {
        List<TenantOrgRestRep> vCenterTenantOptions = new ArrayList<TenantOrgRestRep>();
        if (StringUtils.isBlank(id) ||
                id.equalsIgnoreCase("null")) {
            renderJSON(vCenterTenantOptions);
            return;
        }

        List<ACLEntry> vcenterAcls = VCenterUtils.getAcl(uri(id));
        if (CollectionUtils.isEmpty(vcenterAcls)) {
            renderJSON(vCenterTenantOptions);
            return;
        }

        addNoneTenantOption(id, vCenterTenantOptions);

        Iterator<ACLEntry> aclEntryIterator = vcenterAcls.iterator();
        while (aclEntryIterator.hasNext()) {
            ACLEntry aclEntry = aclEntryIterator.next();
            if (aclEntry == null) {
                continue;
            }

            TenantOrgRestRep tenantOrgRestRep = TenantUtils.getTenant(aclEntry.getTenant());
            if (tenantOrgRestRep != null) {
                vCenterTenantOptions.add(tenantOrgRestRep);
            }
        }
        renderJSON(vCenterTenantOptions);
    }

    private static void addNoneTenantOption(String id, List<TenantOrgRestRep> vCenterTenantOptions) {
        VcenterRestRep vcenterRestRep = VCenterUtils.getVCenter(uri(id));
        if (vcenterRestRep != null && !vcenterRestRep.getCascadeTenancy()) {
            TenantOrgRestRep noneTenantOption = new TenantOrgRestRep();
            noneTenantOption.setName("None");
            noneTenantOption.setId(NullColumnValueGetter.getNullURI());
            vCenterTenantOptions.add(noneTenantOption);
        }
    }

    public static void editVcenterDataCenter(String vcenterDataCenterId, String tenant) {
        VcenterDataCenterRestRep vcenterDataCenter = VcenterDataCenterUtils.getDataCenter(uri(vcenterDataCenterId));
        if (vcenterDataCenter != null) {
            try {
                URI tenantId = NullColumnValueGetter.getNullURI();
                if (StringUtils.isNotBlank(tenant)) {
                    tenantId = uri(tenant);
                }

                VcenterDataCenterUtils.updateDataCenter(uri(vcenterDataCenterId), tenantId);
                list();
            } catch (Exception e) {
                flash.error(MessagesUtils.get("validation.vcenter.messageAndError", e.getMessage()));
                Common.handleError();
            }
        } else {
            flash.error(MessagesUtils.get(UNKNOWN, vcenterDataCenterId));
            list();
        }
    }

    public static void edit(String id) {
        VcenterRestRep dbVCenter = VCenterUtils.getVCenter(uri(id));
        if (dbVCenter != null) {
            VCenterForm vCenter = new VCenterForm(dbVCenter);
            renderTenantOptions();
            render(vCenter, dbVCenter);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    private static void edit(VCenterForm vcenter) {
        // Remove password/confirm from params before flashing
        params.remove("vCenter.password");
        params.remove("vCenter.passwordConfirm");

        params.flash();
        Validation.keep();
        if (vcenter.isNew()) {
            create();
        }
        else {
            edit(vcenter.id);
        }
    }

    public static void save(VCenterForm vCenter) {
        vCenter.validate("vCenter");
        if (Validation.hasErrors()) {
            edit(vCenter);
        }
        else {
            Boolean validateConnectionParam = params.get("validateConnection", Boolean.class);
            boolean validateConnection = validateConnectionParam != null ? validateConnectionParam.booleanValue() : false;

            vCenter.save(validateConnection);

            flash.success(MessagesUtils.get(SAVED, vCenter.name));
            list();
        }
    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids, boolean detachStorage) {
        for (URI id : ResourceUtils.uris(ids)) {
            VCenterUtils.deactivateVCenter(id, detachStorage);
        }
        flash.success(MessagesUtils.get(DELETED));
        list();
    }

    public static void introspect(@As(",") String[] ids) {
        introspect(uris(ids));
    }

    private static void introspect(List<URI> ids) {
        performSuccess(ids, new DiscoveryOperation(), DISCOVERY_STARTED);
        list();
    }

    protected static class DiscoveryOperation implements ResourceIdOperation<Task<VcenterRestRep>> {
        @Override
        public Task<VcenterRestRep> performOperation(URI id) throws Exception {
            return VCenterUtils.discover(id);
        }
    }

    public static class VCenterForm {

        public static final int DEFAULT_PORT = 443;

        public String id;

        public Set<String> tenants;
        public String tenant;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        @HostNameOrIpAddress
        public String hostname;

        @Required
        @MaxSize(1024)
        public String username;

        @MaxSize(1024)
        public String password;

        public String passwordConfirm;

        @Required
        @Min(1)
        public Integer port = DEFAULT_PORT;

        public Boolean cascadeTenancy = Boolean.FALSE;

        public VCenterForm() {
        }

        public VCenterForm(VcenterRestRep vCenter) {
            this();
            doReadFrom(vCenter);
        }

        public void doReadFrom(VcenterRestRep vCenter) {
            this.id = vCenter.getId().toString();
            this.name = vCenter.getName();
            this.hostname = vCenter.getIpAddress();
            this.username = vCenter.getUsername();
            this.port = vCenter.getPortNumber();
            this.cascadeTenancy = vCenter.getCascadeTenancy();
            doReadAcls();
        }

        public void doReadAcls() {
            List<ACLEntry> aclEntries = VCenterUtils.getAcl(URI.create(this.id));
            if (CollectionUtils.isEmpty(aclEntries)) {
                if (!CollectionUtils.isEmpty(this.tenants)) {
                    this.tenants.clear();
                    this.tenant = "";
                }
                return;
            }

            this.tenants = new HashSet<String>();
            if (aclEntries.size() > 1) {
                Iterator<ACLEntry> aclIt = aclEntries.iterator();
                while (aclIt.hasNext()) {
                    this.tenants.add(aclIt.next().getTenant());
                }
                renderArgs.put("disableCascadeTenancy", true);
            } else {
                ACLEntry aclEntry = aclEntries.iterator().next();
                this.tenant = aclEntry.getTenant();
                this.tenants.add(aclEntry.getTenant());
            }
        }

        public void doWriteTo(VcenterCreateParam vcenterCreateParam) {
            doWriteToBase(vcenterCreateParam);
            vcenterCreateParam.setIpAddress(this.hostname);
        }

        public void doWriteTo(VcenterUpdateParam vcenterUpdateParam) {
            doWriteToBase(vcenterUpdateParam);
            vcenterUpdateParam.setIpAddress(this.hostname);
        }

        public void doWriteToBase(VcenterParam vCenter) {
            vCenter.setName(this.name);
            vCenter.setUserName(this.username);
            if (StringUtils.isNotBlank(this.password)) {
                vCenter.setPassword(StringUtils.trimToNull(this.password));
            }
            vCenter.setPortNumber(this.port);
            vCenter.setCascadeTenancy(this.cascadeTenancy);
        }

        public ACLAssignmentChanges getAclAssignmentChanges() {
            Set<String> tenantIds = Sets.newHashSet();

            if (this.cascadeTenancy) {
                if(StringUtils.isNotBlank(this.tenant) &&
                        !this.tenant.equalsIgnoreCase(NullColumnValueGetter.getNullStr().toString())) {
                    tenantIds.add(this.tenant);
                }
            } else if (!CollectionUtils.isEmpty(this.tenants)) {
                tenantIds.addAll(this.tenants);
            }

            List<ACLEntry> existingAcls = new ArrayList<ACLEntry>();
            if (StringUtils.isNotBlank(this.id)) {
                existingAcls = VCenterUtils.getAcl(URI.create(this.id));
            }
            ACLUpdateBuilder builder = new ACLUpdateBuilder(existingAcls);
            builder.setTenants(tenantIds);

            return builder.getACLUpdate();
        }

        public void doValidation(String formName) {
            if (this.isNew()) {
                Validation.required(String.format("%s.password", formName), this.password);
            }

            boolean hasPassword = StringUtils.isNotBlank(password) || StringUtils.isNotBlank(passwordConfirm);
            boolean passwordMatches = StringUtils.equals(password, passwordConfirm);
            if (hasPassword && !passwordMatches) {
                Validation.addError(String.format("%s.passwordConfirm", formName), "error.password.doNotMatch");
            }
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
            doValidation(formName);
        }

        public void save(boolean validateConnection) {
            if (isNew()) {
                try {
                    createVCenter(validateConnection);
                } catch (Exception e) {
                    flash.error(MessagesUtils.get("validation.vcenter.messageAndError", e.getMessage()));
                    Common.handleError();
                }

            } else {
                try {
                    updateVCenter(validateConnection);
                } catch (Exception e) {
                    flash.error(MessagesUtils.get("validation.vcenter.messageAndError", e.getMessage()));
                    Common.handleError();
                }
            }
        }

        protected Task<VcenterRestRep> createVCenter(boolean validateConnection) {
            VcenterCreateParam vcenterCreateParam = new VcenterCreateParam();
            doWriteTo(vcenterCreateParam);

            if (Security.isSystemAdmin()) {
                return VCenterUtils.createVCenter(vcenterCreateParam, validateConnection, getAclAssignmentChanges());
            }

            vcenterCreateParam.setCascadeTenancy(Boolean.TRUE);
            return VCenterUtils.createVCenter(TenantUtils.getTenantFilter(Models.currentAdminTenantForVcenter()),
                    vcenterCreateParam, validateConnection);
        }

        protected Task<VcenterRestRep> updateVCenter(boolean validateConnection) {
            VcenterUpdateParam vcenterUpdateParam = new VcenterUpdateParam();
            doWriteTo(vcenterUpdateParam);

            if (Security.isSystemAdmin()) {
                ACLAssignmentChanges aclAssignmentChanges = getAclAssignmentChanges();
                return VCenterUtils.updateVCenter(uri(id), vcenterUpdateParam, validateConnection,
                        aclAssignmentChanges);
            } else {
                VcenterRestRep vcenterRestRep = VCenterUtils.getVCenter(uri(id));
                vcenterUpdateParam.setCascadeTenancy(vcenterRestRep.getCascadeTenancy());

                return VCenterUtils.updateVCenter(uri(id), vcenterUpdateParam, validateConnection, null);
            }
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void setTenantsForCreation() {
            this.tenants = new HashSet<String>();
            if (StringUtils.isNotBlank(Models.currentAdminTenantForVcenter()) &&
                    Models.currentAdminTenantForVcenter().equalsIgnoreCase(TenantUtils.getNoTenantSelector())) {
                List<TenantOrgRestRep> allTenants = TenantUtils.getAllTenants();
                Iterator<TenantOrgRestRep> tenantsIterator = allTenants.iterator();
                while (tenantsIterator.hasNext()) {
                    TenantOrgRestRep tenant = tenantsIterator.next();
                    if (tenant == null) {
                        continue;
                    }
                    this.tenants.add(tenant.getId().toString());
                    this.cascadeTenancy = Boolean.FALSE;
                }
            } else if (StringUtils.isNotBlank(Models.currentAdminTenantForVcenter()) &&
                    !Models.currentAdminTenantForVcenter().equalsIgnoreCase(TenantUtils.getTenantSelectorForUnassigned())) {
                this.tenants.clear();
                this.tenant = Models.currentAdminTenantForVcenter();
                this.cascadeTenancy = Boolean.TRUE;
            }
        }
    }
}
