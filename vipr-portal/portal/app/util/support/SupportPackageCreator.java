/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.UnhandledException;
import org.apache.commons.lang.text.StrBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import play.Logger;
import play.Play;
import play.jobs.Job;
import play.mvc.Http;
import util.ConfigPropertyUtils;
import util.MonitorUtils;

import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.core.filters.DefaultResourceFilter;
import com.emc.vipr.client.core.search.SearchBuilder;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.sys.healthmonitor.HealthRestRep;
import com.emc.vipr.model.sys.healthmonitor.NodeHealth;
import com.emc.vipr.model.sys.healthmonitor.StatsRestRep;
import com.google.common.collect.Sets;

public class SupportPackageCreator {
    private static final String TIMESTAMP = "ddMMyy-HHmm";

    private static final String VIPR_LOG_DATE_FORMAT = "yyyy-MM-dd_HH:mm:ss";
    private static final Integer LOG_MINTUES_PREVIOUSLY = 60;

    public enum OrderTypes {
        NONE, ERROR, ALL
    }
    
    // Logging Info
    private List<String> logNames;
    private List<String> nodeIds;
    private String startTime = getDefaultStartTime();
    private String endTime = "";
    private String msgRegex = null;
    private Integer logSeverity = 5; // WARN
    private OrderTypes orderTypes = OrderTypes.NONE;
    
    private Http.Request request;
    private ViPRSystemClient client;
    private String tenantId;
    private ViPRCatalogClient2 catalogClient;
    
    public SupportPackageCreator(Http.Request request, ViPRSystemClient client, String tenantId, ViPRCatalogClient2 catalogClient) {
        this.request = request;
        this.client = Objects.requireNonNull(client);
        this.tenantId = tenantId;
        this.catalogClient = Objects.requireNonNull(catalogClient);
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public void setLogNames(List<String> logNames) {
        this.logNames = logNames;
    }

    public void setNodeIds(List<String> nodeIds) {
        this.nodeIds = nodeIds;
    }

    public void setLogSeverity(Integer logSeverity) {
        this.logSeverity = logSeverity;
    }

    public void setMsgRegex(String msgRegex) {
        this.msgRegex = msgRegex;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    
    public void setOrderTypes(OrderTypes orderTypes) {
        this.orderTypes = orderTypes;
    }

    private String getDefaultStartTime() {
        DateTime currentTimeInUTC = new DateTime(DateTimeZone.UTC);
        DateTime startTimeInUTC = currentTimeInUTC.minusMinutes(LOG_MINTUES_PREVIOUSLY);
        DateTimeFormatter fmt = DateTimeFormat.forPattern(VIPR_LOG_DATE_FORMAT);
        return fmt.print(startTimeInUTC);
    }

    public static String formatTimestamp(Calendar cal) {
    	final SimpleDateFormat TIME = new SimpleDateFormat(TIMESTAMP);
        return cal != null ? TIME.format(cal.getTime()) : "UNKNOWN";
    }

    private ViPRSystemClient api() {
        return client;
    }
    
    private ViPRCatalogClient2 catalogApi() {
        return catalogClient;
    }

    private Properties getConfig() {
        Properties props = new Properties();
        props.putAll(ConfigPropertyUtils.getPropertiesFromCoordinator());
        return props;
    }

    private String getMonitorHealthXml() {
        HealthRestRep health = api().health().getHealth();
        return marshall(health);
    }

    private String getMonitorStatsXml() {
        StatsRestRep stats = api().health().getStats();
        return marshall(stats);
    }

    private String marshall(Object obj) {
        try {
            StringWriter writer = new StringWriter();
            JAXBContext context = JAXBContext.newInstance(obj.getClass());
            context.createMarshaller().marshal(obj, writer);
            return writer.toString();
        }
        catch (JAXBException e) {
            throw new UnhandledException(e);
        }
    }
    
    private String getBrowserInfo() {
        StrBuilder sb = new StrBuilder();

        if (request != null) {
            for (Map.Entry<String, Http.Header> header : request.headers.entrySet()) {
                sb.append(header.getKey()).append(": ").append(header.getValue().values).append("\n");
            }
        }
        return sb.toString();
    }

    private List<OrderRestRep> getOrders() {
        if ((orderTypes == OrderTypes.ALL) || (orderTypes == OrderTypes.ERROR)) {
            SearchBuilder<OrderRestRep> search = catalogApi().orders().search().byTimeRange(
                    this.startTime, this.endTime);
            if (orderTypes == OrderTypes.ERROR) {
                search.filter(new FailedOrderFilter());
            }
            List<OrderRestRep> orders = search.run();
            Logger.debug("Found %s Orders", orders.size());
            return orders;
        }
        else {
            return Collections.emptyList();
        }
    }
    
    private Set<String> getSelectedNodeIds() {
        Set<String> activeNodeIds = Sets.newTreeSet();

        for (NodeHealth activeNode : MonitorUtils.getNodeHealth(api())) {
            if (!StringUtils.containsIgnoreCase(activeNode.getStatus() , "unavailable") || Play.mode.isDev()) {
                activeNodeIds.add(activeNode.getNodeId());
            }
        }

        Set<String> selectedNodeIds = Sets.newTreeSet();
        if ((nodeIds == null) || nodeIds.isEmpty()) {
            selectedNodeIds.addAll(activeNodeIds);
        }
        else {
            selectedNodeIds.addAll(nodeIds);
            selectedNodeIds.retainAll(activeNodeIds);
        }
        return selectedNodeIds;
    }

    public CreateSupportPackageJob createJob(OutputStream out) {
        return new CreateSupportPackageJob(out, this);
    }

    public void writeTo(OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        try {
            writeConfig(zip);
            writeSystemInfo(zip);
            writeOrders(zip);
            writeLogs(zip);
            zip.flush();
        }
        finally {
            zip.close();
        }
    }

    private OutputStream nextEntry(ZipOutputStream zip, String path) throws IOException {
        Logger.debug("Adding entry: %s", path);
        ZipEntry entry = new ZipEntry(path);
        zip.putNextEntry(entry);
        return new CloseShieldOutputStream(zip);
    }

    private void addBinaryEntry(ZipOutputStream zip, String path, byte[] data) throws IOException {
        Logger.debug("Adding entry: %s", path);
        ZipEntry entry = new ZipEntry(path);
        entry.setSize(data.length);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
        zip.flush();
    }

    private void addStringEntry(ZipOutputStream zip, String path, String data) throws IOException {
        addBinaryEntry(zip, path, data.getBytes("UTF-8"));
    }

    private void writeConfig(ZipOutputStream zip) throws IOException {
        Properties config = getConfig();
        config.store(nextEntry(zip, "info/config.properties"), "");
    }

    private void writeSystemInfo(ZipOutputStream zip) throws IOException {
        addStringEntry(zip, "info/MonitorHealth.xml", getMonitorHealthXml());
        addStringEntry(zip, "info/MonitorStats.xml", getMonitorStatsXml());
        addStringEntry(zip, "info/BrowserInfo.txt", getBrowserInfo());
    }

    private void writeOrders(ZipOutputStream zip) throws IOException {
        for (OrderRestRep order : getOrders()) {
            writeOrder(zip, order);
        }
    }

    private void writeOrder(ZipOutputStream zip, OrderRestRep order) throws IOException {
        String timestamp = formatTimestamp(order.getCreationTime());
        String path = String.format("orders/Order-%s-%s-%s.txt", order.getOrderNumber(), order.getOrderStatus(),
                timestamp);
        TextOrderCreator creator = new TextOrderCreator(catalogApi(), order);
        addStringEntry(zip, path, creator.getText());
        Logger.debug("Written Order " + order.getId() + " to archive");
    }

    private void writeLogs(ZipOutputStream zip) throws IOException {
        if (logNames != null) {
            // Ensure no duplicate log names
            Set<String> selectedLogNames = Sets.newLinkedHashSet(logNames);
            Set<String> selectedNodeIds = getSelectedNodeIds();
            for (String nodeId : selectedNodeIds) {
                for (String logName : selectedLogNames) {
                    writeLog(zip, nodeId, logName);
                }
            }
        }
    }

    private void writeLog(ZipOutputStream zip, String nodeId, String logName) throws IOException {
        String path = String.format("logs/%s.%s.log", logName, nodeId);
        OutputStream stream = nextEntry(zip, path);

        Set<String> nodeIds = Collections.singleton(nodeId);
        Set<String> logNames = Collections.singleton(logName);

        InputStream in = api().logs().getAsText(nodeIds, logNames, logSeverity, startTime, endTime, msgRegex, null);
        try {
            IOUtils.copy(in, stream);
        }
        finally {
            in.close();
            stream.close();
        }
    }

    /**
     * Filter for failed orders.
     */
    private static class FailedOrderFilter extends DefaultResourceFilter<OrderRestRep> {
        @Override
        public boolean accept(OrderRestRep item) {
            return StringUtils.equals(OrderStatus.ERROR.name(), item.getOrderStatus());
        }
    }
    
    /**
     * Job that runs to generate a support package.
     * 
     * @author jonnymiller
     */
    public static class CreateSupportPackageJob extends Job {
        private OutputStream out;
        private SupportPackageCreator supportPackage;

        public CreateSupportPackageJob(OutputStream out, SupportPackageCreator supportPackage) {
            this.out = out;
            this.supportPackage = supportPackage;
        }

        @Override
        public void doJob() throws Exception {
            supportPackage.writeTo(out);
        }
    }
}
