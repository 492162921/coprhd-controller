/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Test cases for utility methods.
 *
 * Created by gang on 7/6/16.
 */
public class DriverUtilTest {

    private static Logger logger = LoggerFactory.getLogger(DriverUtilTest.class);

    @Test
    public void testFormatWwn() {
        logger.debug("wwn = {}", DriverUtil.formatWwn("500009735014fc18"));
    }

    @Test
    public void testConvertBytes() {
        logger.debug("Bytes of 64KB = {}", DriverUtil.convert2KB(CapUnit.KB,64.0));
        logger.debug("Bytes of 100MB = {}", DriverUtil.convert2KB(CapUnit.MB, 100.0));
        logger.debug("Bytes of 1GB = {}", DriverUtil.convert2KB(CapUnit.GB, 1.0));
        logger.debug("Bytes of 49044.02 GB = {}", DriverUtil.convert2KB(CapUnit.GB, 49044.02));
    }
}
