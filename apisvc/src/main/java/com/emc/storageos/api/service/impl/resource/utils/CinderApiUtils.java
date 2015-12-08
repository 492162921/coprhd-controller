/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.emc.storageos.api.service.impl.resource.VirtualPoolService;
import com.emc.storageos.cinder.model.CinderQos;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.QosSpecification;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class CinderApiUtils {
	
	private static final Logger _log = LoggerFactory.getLogger(CinderApiUtils.class);
	
	private CinderApiUtils(){
	
	}
	
	private static final String XML = "xml";
	
	private static final String JSON = "json";
	
	private static final String ACCEPT = "Accept";
	
	private static final String XML_REGULAR_EXP = ".*application/xml.*";
	
	private static final String JSON_REGULAR_EXP = ".*application/json.*";

    public static final Integer UNLIMITED_SNAPSHOTS = -1;

    public static final Integer DISABLED_SNAPSHOTS = 0;
	
    /**
     * Unmodifiable map used to keep key value for the Http Response code
     */
    private static final Map<Integer, String> ERROR_CODE_MAP =
            Collections.unmodifiableMap(new HashMap<Integer, String>() {
                private static final long serialVersionUID = 1L;
                {
                    put(400, "badRequest");
                    put(404, "itemNotFound");
                }
            });
	
	/**
	 * method for getting http response based on Pojo and http header
	 * @param obj Pojo object
	 * @param header http request header
	 * @return Response Object
	 */
	public static Response getCinderResponse(Object obj, HttpHeaders header, boolean isJsonRootElementRequired){
		String mediaType = getMediaType(header);
		if(StringUtils.isNotEmpty(mediaType)){
			if (mediaType.equals(XML)) {
				_log.debug("Requested Media type : XML");
				return Response.ok().entity(obj).build();
			} else if (mediaType.equals(JSON)) {
				_log.debug("Requested Media type : JSON");
				ObjectMapper objectMapper = new ObjectMapper();
				if (isJsonRootElementRequired) {
					objectMapper.enable(SerializationConfig.Feature.WRAP_ROOT_VALUE);
				}
				try {
					String jsonResponse = objectMapper.writeValueAsString(obj);
					return Response.ok().entity(jsonResponse).build();
				} catch (JsonGenerationException e) {
					throw APIException.badRequests.parameterIsNotValid(obj.getClass().getName());
				} catch (JsonMappingException e) {
					throw APIException.badRequests.parameterIsNotValid(obj.getClass().getName());
				} catch (IOException e) {
					throw APIException.internalServerErrors.ioWriteError(obj.getClass().getName());
				} 
			}
		} 
		return Response.status(415).entity("Unsupported Media Type")
				.build();
		
	}
	
	/**
	 * method for getting media type based on http header
	 * @param header http header request
	 * @return media type in String
	 */
	public static String getMediaType(HttpHeaders header){
		Pattern jsonPattern = Pattern.compile(JSON_REGULAR_EXP);
		Pattern xmlPattern = Pattern.compile(XML_REGULAR_EXP);

		MultivaluedMap<String, String> headersInfo = header.getRequestHeaders();
		List<String> mediaTypes = headersInfo.get(ACCEPT);

		for (String mediaType : mediaTypes) {
			if (jsonPattern.matcher(mediaType).matches()) {
				return JSON;
			} else if (xmlPattern.matcher(mediaType).matches()) {
				return XML;
			}
		}
		return null;
		
	}
	/**
	 * This function converts Map to xml format
	 * @param map Hash Map
	 * @param root root Element Name
	 * @return XML object in String form
	 */
	public static Object convertMapToXML(Map<String, String> map, String root){
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder;
        Document document = null;
        LSSerializer lsSerializer = null;
		try {
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			document = documentBuilder.newDocument();
			Element rootElement = document.createElement(root);
			if (null != rootElement) {
				document.appendChild(rootElement);
				for (Entry<String, String> entry : map.entrySet()) {
					Element mapElement = document.createElement(entry.getKey());
					mapElement.setTextContent(entry.getValue());
					rootElement.appendChild(mapElement);
				}
			}
			DOMImplementationLS domImplementation = (DOMImplementationLS) document
					.getImplementation();
			lsSerializer = domImplementation.createLSSerializer();
		} catch (ParserConfigurationException e) {
			throw APIException.internalServerErrors.ioWriteError(root);
		}

		return lsSerializer.writeToString(document);

	}
	
    /**
     * Create error message according to OpenStack environment in JSON
     * 
     * @param errCode
     *            Response error code
     * @param errMsg
     *            Error message
     *            JSON Message should be in this format:
     *            {"badRequest":{"message":"Invalid volume: Volume still has 1 dependent snapshots", "code":400}}
     */
    public static Response createErrorResponse(int errCode, String errMsg) {
        JSONObject outerJsonObj = new JSONObject();
        JSONObject innerJsonObj = new JSONObject();
        try {
            outerJsonObj.put(ERROR_CODE_MAP.get(errCode), innerJsonObj);
            innerJsonObj.put("code", errCode);
            innerJsonObj.put("message", errMsg);
        } catch (JSONException e) {
            _log.error("Error occured while creating JSON error message");
        }
        return Response.status(errCode).entity(outerJsonObj.toString()).build();
    }
    
    /**
     * Util function to split the string and send the value at required position
     * 
     * @param str
     *            String to split
     * @param delimiter
     * @param position
     * @return
     */
    public static String splitString(String str, String delimiter, int position) {
        String[] stringArray = str.split(delimiter);
        return stringArray[position];
    }

    /**
     * Format the Calendar instance
     * 
     * @param cal
     * @return Formatted calendar
     */
    public static String timeFormat(Calendar cal) {
        return new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(cal.getTimeInMillis());
    }

    /**
     * Retrieves information from given Virtual Pool
     *
     * @param virtualPool Virtual Pool
     * @return QosSpecification filled with information from Virtual Pool
     */
    public static QosSpecification getDataFromVirtualPool(VirtualPool virtualPool) {
        _log.debug("Fetching data from Virtual Pool, id: {}", virtualPool.getId());
        QosSpecification qos = new QosSpecification();
        StringMap specs = new StringMap();
        String systems = virtualPool.getProtocols().toString();
        qos.setName("specs-" + virtualPool.getLabel());
        qos.setConsumer("back-end");
        qos.setLabel(virtualPool.getLabel());
        qos.setId(URIUtil.createId(QosSpecification.class));
        qos.setVirtualPoolId(virtualPool.getId());
        specs.put("Provisioning Type", virtualPool.getSupportedProvisioningType());
        specs.put("Protocol", systems.substring(1, systems.length() - 1));
        specs.put("Drive Type", virtualPool.getDriveType());
        specs.put("System Type", VirtualPoolService.getSystemType(virtualPool));
        specs.put("Multi-Volume Consistency", Boolean.toString(virtualPool.getMultivolumeConsistency()));
        if (virtualPool.getArrayInfo().get("raid_level") != null) {
            specs.put("RAID LEVEL", virtualPool.getArrayInfo().get("raid_level").toString());
        }
        specs.put("Expendable", Boolean.toString(virtualPool.getExpandable()));
        specs.put("Maximum SAN paths", Integer.toString(virtualPool.getNumPaths()));
        specs.put("Minimum SAN paths", Integer.toString(virtualPool.getMinPaths()));
        specs.put("Maximum block mirrors", Integer.toString(virtualPool.getMaxNativeContinuousCopies()));
        specs.put("Paths per Initiator", Integer.toString(virtualPool.getPathsPerInitiator()));
        if (virtualPool.getHighAvailability() != null) {
            specs.put("High Availability", virtualPool.getHighAvailability());
        }
        if (virtualPool.getMaxNativeSnapshots().equals(UNLIMITED_SNAPSHOTS)) {
            specs.put("Maximum Snapshots", "unlimited");
        }else if(virtualPool.getMaxNativeSnapshots().equals(DISABLED_SNAPSHOTS)){
            specs.put("Maximum Snapshots", "disabled");
        }else{
            specs.put("Maximum Snapshots", Integer.toString(virtualPool.getMaxNativeSnapshots()));
        }

        qos.setSpecs(specs);
        return qos;
    }

}
