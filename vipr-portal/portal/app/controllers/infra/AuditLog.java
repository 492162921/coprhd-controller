/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package controllers.infra;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Date;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import models.datatable.AuditLogDataTable;
import play.Logger;
import play.mvc.Controller;
import play.mvc.With;
import util.AuditLogs;
import util.BourneUtil;
import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_AUDITOR") })
public class AuditLog extends Controller {
    private static String LOG_LANG = "en_US";
    private static JAXBContext CONTEXT;

    private static synchronized JAXBContext getContext() throws JAXBException {
        if (CONTEXT == null) {
            CONTEXT = JAXBContext.newInstance(AuditLogs.class);
        }
        return CONTEXT;
    }

    private static AuditLogs unmarshall(InputStream in) throws JAXBException {
        return (AuditLogs) getContext().createUnmarshaller().unmarshal(in);
    }

    public static void list() {
        AuditLogDataTable dataTable = new AuditLogDataTable();
        render(dataTable);
    }

    public static void logsJson(Long date) {
        if (date == null) {
            date = new Date().getTime();
        }
        InputStream in = BourneUtil.getViprClient().audit().getLogsForHourAsStream(new Date(date), LOG_LANG);
        try {
            AuditLogs logs = unmarshall(in);
            if (logs.getAuditLogs() != null) {
                for (com.emc.storageos.db.client.model.AuditLog log : logs.getAuditLogs()) {
                    if (log.getUserId() == null) {
                        try {
                            log.setUserId(new URI(""));
                        }
                        catch (URISyntaxException shouldNotHappen) {
                            Logger.error(shouldNotHappen, "Error setting UserId to blank");
                        }
                    }
                }

                renderJSON(logs.getAuditLogs());
            }
            else {
                renderJSON(Collections.<String> emptyList());
            }
        }
        catch (JAXBException e) {
            error(e);
        }
    }
}