/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getCatalogClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ExecutionLogRestRep;
import com.emc.vipr.model.catalog.ExecutionStateRestRep;
import com.emc.vipr.model.catalog.OrderLogRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.google.common.collect.Lists;

public class OrderUtils {

    public static OrderRestRep getOrder(RelatedResourceRep resource) {
        if (resource != null) {
            return getOrder(resource.getId());
        }
        return null;
    }
    
    public static OrderRestRep getOrder(URI id) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        
        OrderRestRep order = null;
        try {
            order = catalog.orders().get(id);
        }
        catch(ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                order = null;
            }
            else {
                throw e;
            }
        }
        return order;
    }
    
    public static List<OrderRestRep> getOrders(URI tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.orders().getByTenant(tenantId);
    }
    
    public static List<OrderRestRep> getUserOrders(String username) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.orders().getUserOrders();
    }
    
    public static List<CatalogServiceRestRep> getCatalogServices() {
        return getCatalogServices(null);
    }

    public static List<CatalogServiceRestRep> getCatalogServices(URI tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        
        CatalogCategoryRestRep root = catalog.categories().getRootCatalogCategory(tenantId);
        List<CatalogCategoryRestRep> categories = catalog.categories().getSubCategories(root.getId());
        List<CatalogServiceRestRep> catalogServices = new ArrayList<CatalogServiceRestRep>();
        for (CatalogCategoryRestRep category : categories) {
             catalogServices.addAll(catalog.services().findByCatalogCategory(category.getId()));
        }
        
        catalog.executionWindows().getCatalogServices();
        
        return catalogServices;
    }

    public static List<OrderRestRep> getScheduledOrders() {
        return getScheduledOrders(null);
    }
    
    public static List<OrderRestRep> getScheduledOrders(URI tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        List<OrderRestRep> scheduledOrders = catalog.orders().search().byStatus(OrderStatus.SCHEDULED.name(), tenantId).run();
        return scheduledOrders;
    }
    
    public static List<OrderRestRep> getErrorOrders(URI tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        List<OrderRestRep> scheduledOrders = catalog.orders().search().byStatus(OrderStatus.ERROR.name(), tenantId).run();
        return scheduledOrders;
    }    
    
    public static List<OrderRestRep> getScheduledOrdersByExecutionWindow(URI executionWindowId) {
        return getScheduledOrdersByExecutionWindow(executionWindowId, null);
    }
    
    public static List<OrderRestRep> getScheduledOrdersByExecutionWindow(URI executionWindowId, URI tenantId) {
        List<OrderRestRep> scheduledOrders = getScheduledOrders(tenantId);
        List<OrderRestRep> scheduledOrdersInWindow = Lists.newArrayList();
        for (OrderRestRep scheduledOrder : scheduledOrders) {
            if (scheduledOrder.getExecutionWindow() != null && executionWindowId.equals(scheduledOrder.getExecutionWindow().getId())) {
                scheduledOrdersInWindow.add(scheduledOrder);
            }
        }
        return scheduledOrdersInWindow;
    }
    
    public static List<CatalogServiceRestRep> getServicesByExecutionWindow(URI executionWindowId) {
        return getServicesByExecutionWindow(executionWindowId, null);
    }
    
    public static List<CatalogServiceRestRep> getServicesByExecutionWindow(URI executionWindowId, URI tenantId) {
        List<CatalogServiceRestRep> catalogServices = getCatalogServices(tenantId);
        List<CatalogServiceRestRep> catalogServicesInWindow = Lists.newArrayList();
        for (CatalogServiceRestRep catalogService : catalogServices) {
            if (catalogService.isExecutionWindowRequired() && catalogService.getDefaultExecutionWindow() != null && executionWindowId.equals(catalogService.getDefaultExecutionWindow().getId())) {
                catalogServicesInWindow.add(catalogService);
            }
        }
        return catalogServicesInWindow;
    }
    
    public static List<OrderRestRep> findByTimeRange(Date startTime, Date endTime) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        String start = null;
        if (startTime != null) {
            start = Long.toString(startTime.getTime());
        }
        String end = null;
        if (endTime != null) {
            end = Long.toString(endTime.getTime());
        }

        return catalog.orders().search().byTimeRange(start, end).run();
    }
    
    public static void cancelOrder(URI orderId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.orders().cancelOrder(orderId);;
    }
    
    public static ExecutionStateRestRep getExecutionState(URI orderId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.orders().getExecutionState(orderId);
    }
    
    public static List<ExecutionLogRestRep> getExecutionLogs(URI orderId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.orders().getExecutionLogs(orderId);
    }
    
    public static List<OrderLogRestRep> getOrderLogs(URI orderId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.orders().getLogs(orderId);
    }
    
}
