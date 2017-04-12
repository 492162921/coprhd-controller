/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity

import org.apache.commons.collections.ExtendedProperties
import org.junit.runner.JUnitCore
import org.junit.runner.Result

import com.emc.vipr.client.AuthClient
import com.emc.vipr.client.ClientConfig
import com.emc.vipr.client.ViPRCatalogClient2
import com.emc.vipr.client.ViPRCoreClient
import com.emc.vipr.client.ViPRPortalClient
import com.emc.vipr.client.ViPRSystemClient
import com.emc.vipr.sanity.setup.HostSetup
import com.emc.vipr.sanity.setup.ProjectSetup
import com.emc.vipr.sanity.setup.SecuritySetup
import com.emc.vipr.sanity.setup.SystemSetup
import com.emc.vipr.sanity.setup.VCenterSetup
import com.emc.vipr.sanity.setup.VMAXSetup
import com.emc.vipr.sanity.setup.VirtualArraySetup


class Sanity {
    static final Integer API_TASK_TIMEOUT = 120000

    static ExtendedProperties properties
    static ClientConfig clientConfig
    static String authToken
    static int services_run = 0

    // Clients
    static ViPRCoreClient client
    static ViPRCatalogClient2 catalog
    static ViPRPortalClient portal
    static ViPRSystemClient sys

    static Class catalogTests = com.emc.vipr.sanity.tests.CatalogAPISanity.class
    static Class blockTests = com.emc.vipr.sanity.tests.CatalogBlockServicesSanity.class
    static Class protectionTests = com.emc.vipr.sanity.tests.CatalogBlockProtectionServicesSanity.class
    static Class vmwareTests = com.emc.vipr.sanity.tests.CatalogVmwareBlockServicesSanity.class

    static allTests = [
        catalogTests,
        blockTests,
        protectionTests,
        vmwareTests
    ] as Class[]

    public static void main(String[] args) {
        setup()
        JUnitCore junit = new JUnitCore()
        String catalogTest = System.getenv("CatalogTest")
        Result result = null
        switch (catalogTest) {
            case "all":
                result = junit.run(allTests)
                break
            case "catalog":
                result = junit.run(catalogTests)
                break
            case "block":
                result = junit.run(blockTests)
                break
            case "protection":
                result = junit.run(protectionTests)
                break
            case "vmware":
                result = junit.run(vmwareTests)
                break
            default:
                println "Not running any tests. Parameter = " + catalogTest
                break
        }

        if (result != null && result.failureCount > 0) {
            System.exit(1);
        }
    }

    static void setup() {
        initClients()
        SystemSetup.commonSetup()
        SecuritySetup.setupActiveDirectory()
        VirtualArraySetup.setup()
        ProjectSetup.setup()
        VirtualArraySetup.updateAcls(client.userTenantId)
        HostSetup.setup()
        VMAXSetup.setupSimulator()
        VCenterSetup.setup()
    }

    static void initClients() {
        clientConfig = new ClientConfig(
                host: "localhost",
                mediaType: "application/xml",
                requestLoggingEnabled: false,
                ignoreCertificates: true
                )

        login(System.getenv("SYSADMIN"), System.getenv("SYSADMIN_PASSWORD"))
    }

    static void login(String username, String password) {
        println "Logging in to ViPR as $username"
        authToken = new AuthClient(clientConfig).login(username, password)
        client = new ViPRCoreClient(clientConfig).withAuthToken(authToken)
        catalog = new ViPRCatalogClient2(clientConfig).withAuthToken(authToken)
        portal = new ViPRPortalClient(clientConfig).withAuthToken(authToken)
        sys = new ViPRSystemClient(clientConfig).withAuthToken(authToken)
    }
}
