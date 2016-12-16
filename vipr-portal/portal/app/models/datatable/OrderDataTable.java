/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.time.DateUtils;

import com.emc.vipr.model.catalog.OrderCount;

import play.Logger;
import models.security.UserInfo;
import util.OrderUtils;
import util.datatable.DataTable;

import com.emc.vipr.model.catalog.OrderRestRep;
import com.google.common.collect.Lists;

import controllers.catalog.Orders;

public class OrderDataTable extends DataTable {
    protected static final String ORDER_MAX_COUNT = "6000";
    protected static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    protected UserInfo userInfo;
    protected String tenantId;

    protected Date startDate; // should be "yyyy-MM-dd 00:00:00" in local time zone
    protected Date endDate;   // should be "yyyy-MM-dd 23:59:59" in local time zone

    private void setStartDate(Date startDate) {
        this.startDate = getStartTimeOfADay(startDate);
    }

    private void setEndDate(Date endDate) {
        this.endDate = getEndTimeOfADay(endDate);
    }

    public OrderDataTable(String tenantId) {
        this.tenantId = tenantId;

        addColumn("number");
        addColumn("status").setRenderFunction("render.orderStatus");
        addColumn("summary");
        addColumn("submittedBy").hidden();
        addColumn("createdDate").setRenderFunction("render.localDate");
        addColumn("scheduledTime").setRenderFunction("render.localDate");

        setDefaultSort("createdDate", "desc");
        setRowCallback("createRowLink");
        sortAll();
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public void setStartDate(String startDate) {
        try {
            this.setStartDate(DATE_FORMAT.parse(startDate));
        } catch (ParseException e) {
            Logger.error("Date parse error for: %s, e=%s", startDate, e);
        }
    }

    public void setEndDate(String endDate) {
        try {
            this.setEndDate(DATE_FORMAT.parse(endDate));
        } catch (ParseException e) {
            Logger.error("Date parse error for: %s, e=%s", endDate, e);
        }
    }

    public void setStartAndEndDatesByMaxDays(int maxDays) {
        this.setStartDate(getDateDaysAgo(maxDays));
        this.setEndDate(now());
    }

    public List<OrderInfo> fetchAll() {
        List<OrderRestRep> orderRestReps = null;
        if (userInfo != null) {
            Logger.info("hlj, start to call fetchAll(), %s, %s", startDate, endDate);
            orderRestReps = OrderUtils.getUserOrders(startDate, endDate, ORDER_MAX_COUNT);
        } else {
            orderRestReps = OrderUtils.getOrders(uri(this.tenantId));
        }

        return convert(orderRestReps);
    }

    public OrderCount fetchCount() {
        return OrderUtils.getUserOrdersCount(startDate, endDate);
    }

    public static class OrderInfo {
        public String rowLink;
        public String id;
        public String number;
        public String status;
        public String summary;
        public Long createdDate;
        public String submittedBy;
        public String scheduledEventId;
        public Long scheduledTime;

        public OrderInfo(OrderRestRep o) {
            this.id = o.getId().toString();
            this.number = o.getOrderNumber();
            this.rowLink = createLink(Orders.class, "receipt", "orderId", id);
            this.status = o.getOrderStatus();
            this.summary = o.getSummary();
            if (o.getCreationTime() != null) {
                this.createdDate = o.getCreationTime().getTime().getTime();
            }
            if (o.getSubmittedBy() != null) {
                this.submittedBy = o.getSubmittedBy().toString();
            }
            if (o.getScheduledEventId() != null) {
                this.scheduledEventId = o.getScheduledEventId().toString();
            }
            if (o.getScheduledTime() != null) {
                this.scheduledTime = o.getScheduledTime().getTime().getTime();
            }
        }
    }

    protected List<OrderInfo> convert(List<OrderRestRep> orderRestReps) {
        List<OrderInfo> orderInfos = Lists.newArrayList();
        if (orderRestReps != null) {
            for (OrderRestRep orderRestRep : orderRestReps) {
                orderInfos.add(new OrderInfo(orderRestRep));
            }
        }
        return orderInfos;
    }

    public static Date getDateDaysAgo(int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return cal.getTime();
    }

    protected Date now() {
        return new Date();
    }

    protected Date getStartTimeOfADay(Date origin) {
        Date result = DateUtils.setHours(origin, 0);
        result = DateUtils.setMinutes(result, 0);
        result = DateUtils.setSeconds(result, 0);
        return result;
    }

    protected Date getEndTimeOfADay(Date origin) {
        Date result = DateUtils.setHours(origin, 23);
        result = DateUtils.setMinutes(result, 59);
        result = DateUtils.setSeconds(result, 59);
        return result;
    }
}
