package com.emc.storageos.driver.scaleio;

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScaleIORestHandleFactory {
    private static final Logger log = LoggerFactory.getLogger(ScaleIORestHandleFactory.class);
    private final Map<String, ScaleIORestClient> ScaleIORestClientMap = new ConcurrentHashMap<String, ScaleIORestClient>();
    private final Object syncObject = new Object();

    private ScaleIORestClientFactory scaleIORestClientFactory;

    public ScaleIORestClientFactory getScaleIORestClientFactory() {
        return scaleIORestClientFactory;
    }

    public void setScaleIORestClientFactory(
            ScaleIORestClientFactory scaleIORestClientFactory) {
        this.scaleIORestClientFactory = scaleIORestClientFactory;
    }

    /**
     * Get Rest client handle for a scaleIO storage system
     *
     * @param systemNativeId storage system native id (Optional)
     * @param ipAddr object native id
     * @param port class instance
     * @param username class instance
     * @param password class instance
     * @return scaleIO handle
     */
    public ScaleIORestClient getClientHandle(String systemNativeId, String ipAddr, int port, String username, String password)
            throws Exception {
        ScaleIORestClient handle;
        String systemId = "";
        if (systemNativeId != null) {
            systemId = systemNativeId.trim();
        }
        synchronized (syncObject) {
            if (!ScaleIORestClientMap.containsKey(systemId)) {
                if (ipAddr != null && username != null && password != null) {
                    URI baseURI = URI.create(ScaleIOConstants.getAPIBaseURI(ipAddr, port));
                    handle = (ScaleIORestClient) scaleIORestClientFactory.getRESTClient(baseURI, username,
                            password, true);
                    try {
                        systemId = handle.getSystemId(); // Get the exact systemId and check the availability of handle
                        if (systemId != null) {
                            ScaleIORestClientMap.put(systemId, handle);
                        }
                    } catch (Exception e) {
                        log.error("Failed to get Rest Handle", e);
                    }
                }
            }
        }
        return ScaleIORestClientMap.get(systemId);
    }

}
