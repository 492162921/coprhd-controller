package com.emc.storageos.ecs.api;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

public class ECSApiFactory {
	private Logger _log = LoggerFactory.getLogger(ECSApiFactory.class);
    private static final int DEFAULT_MAX_CONN = 300;
    private static final int DEFAULT_MAX_CONN_PER_HOST = 100;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 30;
    private static final int DEFAULT_SOCKET_CONN_TIMEOUT = 1000 * 60 * 60;
    
    private int _maxConn = DEFAULT_MAX_CONN;
    private int _maxConnPerHost = DEFAULT_MAX_CONN_PER_HOST;
    private int _connTimeout = DEFAULT_CONN_TIMEOUT;
    private int _socketConnTimeout = DEFAULT_SOCKET_CONN_TIMEOUT;

    private ApacheHttpClientHandler _clientHandler;
    private ConcurrentMap<String, ECSApi> _clientMap;
    private MultiThreadedHttpConnectionManager _connectionManager;

    /**
     * Maximum number of outstanding connections
     *
     * @param maxConn
     */
    public void setMaxConnections(int maxConn) {
        _maxConn = maxConn;
    }

    /**
     * Maximum number of outstanding connections per host
     *
     * @param maxConnPerHost
     */
    public void setMaxConnectionsPerHost(int maxConnPerHost) {
        _maxConnPerHost = maxConnPerHost;
    }

    /**
     * Connection timeout
     *
     * @param connectionTimeoutMs
     */
    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        _connTimeout = connectionTimeoutMs;
    }

    /**
     * Socket connection timeout
     *
     * @param connectionTimeoutMs
     */
    public void setSocketConnectionTimeoutMs(int connectionTimeoutMs) {
        _socketConnTimeout = connectionTimeoutMs;
    }

    /**
     * Initialize
     */
    public void init() {
        _log.info(" ECSApiFactory ECSApi factory initialized");
        _clientMap = new ConcurrentHashMap<String, ECSApi>();

        HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        params.setDefaultMaxConnectionsPerHost(_maxConnPerHost);
        params.setMaxTotalConnections(_maxConn);
        params.setTcpNoDelay(true);
        params.setConnectionTimeout(_connTimeout);
        params.setSoTimeout(_socketConnTimeout);

        _connectionManager = new MultiThreadedHttpConnectionManager();
        _connectionManager.setParams(params);
        _connectionManager.closeIdleConnections(0);  // close idle connections immediately

        HttpClient client = new HttpClient(_connectionManager);
        client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
            @Override
            public boolean retryMethod(HttpMethod httpMethod, IOException e, int i) {
                return false;
            }
        });
        _clientHandler = new ApacheHttpClientHandler(client);

        Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), 443));
    }
    
    /**
     * shutdown http connection manager.
     */
    protected void shutdown()  {
        _connectionManager.shutdown();
    }

    /**
     * Create ECS API client
     *
     * @param endpoint ECS endpoint
     * @return
     */
    public ECSApi getRESTClient(URI endpoint) {
        ECSApi ecsApi = _clientMap.get(endpoint.toString()+":"+":");
        if (ecsApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            RESTClient restClient = new RESTClient(jerseyClient);
            ecsApi = new ECSApi(endpoint, restClient);
            _clientMap.putIfAbsent(endpoint.toString()+":"+":", ecsApi);
        }
        return ecsApi;
    }
    
    /**
     * Create ECS API client
     *
     * @param endpoint ECS endpoint
     * @return
     */
    public ECSApi getRESTClient(URI endpoint, String username, String password) {
        ECSApi ecsApi = _clientMap.get(endpoint.toString() +":"+ username +":"+ password);
        if (ecsApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
            RESTClient restClient = new RESTClient(jerseyClient);
            ecsApi = new ECSApi(endpoint, restClient);
            _clientMap.putIfAbsent(endpoint.toString()+":"+username+":"+password, ecsApi);
        }
        return ecsApi;
    }
}

