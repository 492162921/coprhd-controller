package com.emc.vipr.sanity

import com.emc.vipr.sanity.setup.SystemSetup

// This script will run set default system properties and skip the
// UI initial setup pages
Sanity.initialize()
SystemSetup.commonSetup()