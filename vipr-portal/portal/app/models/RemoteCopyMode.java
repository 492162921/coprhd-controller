/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models;

import util.StringOption;

public class RemoteCopyMode {
    public static final String ASYNCHRONOUS = "ASYNCHRONOUS";
    public static final String SYNCHRONOUS = "SYNCHRONOUS";

    public static final String[] VALUES = { ASYNCHRONOUS, SYNCHRONOUS };

    public static final StringOption[] OPTIONS = StringOption.options(VALUES, "RemoteCopyMode", false);
}
