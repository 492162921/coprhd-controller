/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;

import com.emc.storageos.model.search.SearchResultResourceRep;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.asset.annotation.AnnotatedAssetOptionsProvider;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.util.Messages;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

public class BaseAssetOptionsProvider extends AnnotatedAssetOptionsProvider {

    private static Messages MESSAGES = new Messages("com.emc.sa.asset.AssetProviders");

    private Logger log;

    @Autowired
    private ClientConfig clientConfig;

    @Autowired
    private ModelClient modelClient;

    public void setClientConfig(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void setModelClient(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    protected ViPRCoreClient api(AssetOptionsContext context) {
        return new ViPRCoreClient(clientConfig).withAuthToken(context.getAuthToken());
    }

    protected ModelClient models() {
        return modelClient;
    }

    protected ModelClient models(AssetOptionsContext context) {
        return modelClient;
    }

    protected AssetOption createOption(DataObject value) {
        URI id = value.getId();
        String label = value.getLabel();
        return new AssetOption(id.toString(), label);
    }

    protected AssetOption createNamedResourceOption(NamedRelatedResourceRep value) {
        return new AssetOption(value.getId(), value.getName());
    }

    protected AssetOption createBaseResourceOption(DataObjectRestRep value) {
        return new AssetOption(value.getId(), value.getName());
    }

    protected List<AssetOption> createOptions(Object[] values) {
        List<AssetOption> options = Lists.newArrayList();
        for (Object value : values) {
            options.add(new AssetOption(value.toString(), value.toString()));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> createOptions(Collection<? extends DataObject> values) {
        List<AssetOption> options = Lists.newArrayList();
        for (DataObject value : values) {
            options.add(createOption(value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> createNamedResourceOptions(Collection<? extends NamedRelatedResourceRep> values) {
        List<AssetOption> options = Lists.newArrayList();
        for (NamedRelatedResourceRep value : values) {
            options.add(createNamedResourceOption(value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> createBaseResourceOptions(Collection<? extends DataObjectRestRep> values) {
        List<AssetOption> options = Lists.newArrayList();
        for (DataObjectRestRep value : values) {
            options.add(createBaseResourceOption(value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> createBaseResourceOptions2(List<SearchResultResourceRep> refs) {
        List<AssetOption> options = Lists.newArrayList();
        for (SearchResultResourceRep ref : refs) {
            options.add(createBaseResourceOption(ref));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);

        getLog().info("===== options start");
        for (AssetOption option : options) {
            String msg = String.format("========== option is %s, %s", option.key, option.value);
            getLog().info(msg);
        }
        getLog().info("===== options end");
        return options;
    }

    private AssetOption createBaseResourceOption(SearchResultResourceRep ref) {
        return new AssetOption(ref.getId(), ref.getMatch());
    }

    protected static List<AssetOption> createStringOptions(Collection<String> values) {
        List<AssetOption> options = Lists.newArrayList();
        for (String value : values) {
            options.add(new AssetOption(value, value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    public static String getMessage(String key, Object... args) {
        try {
            String message = MESSAGES.get(key, args);
            if (StringUtils.isNotBlank(message)) {
                return message;
            }
        } catch (MissingResourceException e) {
            // fall out and return the original key
        }
        return key;
    }

    public static AssetOption newAssetOption(String key, String value, Object... args) {
        String message = getMessage(value, args);
        if (StringUtils.isEmpty(message)) {
            message = String.format(value, args);
        }
        return new AssetOption(key, message);
    }

    public static AssetOption newAssetOption(URI id, String value, Object... args) {
        return newAssetOption(id.toString(), value, args);
    }

    public static URI uri(String id) {
        return URI.create(id);
    }

    protected final Logger getLog() {
        if (log == null) {
            log = Logger.getLogger(getClass());
        }
        return log;
    }

    protected void debug(String message, Object... args) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(format(message, args));
        }
    }

    protected void info(String message, Object... args) {
        if (getLog().isInfoEnabled()) {
            getLog().info(format(message, args));
        }
    }

    protected void warn(String message, Object... args) {
        getLog().warn(format(message, args));
    }

    protected void warn(Throwable t, String message, Object... args) {
        getLog().warn(format(message, args), t);
    }

    protected void warn(Throwable t) {
        getLog().warn(t, t);
    }

    protected void error(String message, Object... args) {
        getLog().error(format(message, args));
    }

    protected void error(Throwable t, String message, Object... args) {
        getLog().error(format(message, args), t);
    }

    protected void error(Throwable t) {
        getLog().error(t, t);
    }

    private String format(String message, Object... args) {
        if (args != null && args.length > 0) {
            return String.format(message, args);
        }
        else {
            return message;
        }
    }
}
