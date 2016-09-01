/*
 * Copyright (c) 2016 EMC Corporation
 * 
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowExceptions;

public class NetworkZoningParam implements Serializable {
	private static final long serialVersionUID = 678350596864970920L;
	
	/**
	 * This is the zoning map obtained from the ExportMask.
	 * It is a map of initiator URI string to a set of port URI strings.
	 * Zones will be added or removed as derived from the zoning map.
	 */
	private StringSetMap zoningMap;
	/**
	 * Boolean indicating the the ExportMask had additional additional volumes
	 * to be considered. If this boolean is true, the zone references will be
	 * removed, but zones will not be removed.
	 */
	private boolean hasExistingVolumes;
	/**
	 * The virtualArray is used to determine if zoning is enabled.
	 */
	private URI virtualArray;
	/**
	 * THe altVirtualArray is used in searching for initiators, ports, etc. sed by
	 * the VPLEX in the alternate virtual array.
	 */
	private URI altVirtualArray;
	
	/**
	 * The exportGroupId is used as part of the FCZoneReferences.
	 */
	private URI exportGroupId;
	
	/**
	 * The ExportGroup display string, used for logging.
	 */
	private String exportGroupDisplay;
	
	/**
	 * Name of the ExportMask the parameters were derived from. Used for logging.
	 */
	private String maskName;
	
	/**
	 * URI of the ExportMask the parameters were derived from. Used for logging.
	 */
	private URI maskId;
	
	/*
	 * Export mask volumes.
	 */
	private List<URI> volumes;
	
	/**
	 * Generates the zoning parameters from an ExportGroup/ExportMask pair.
	 * @param exportGroup ExportGroup
	 * @param exportMask ExportMask
	 * @param dbClient Database Handle
	 */
	public NetworkZoningParam(ExportGroup exportGroup, ExportMask exportMask, DbClient dbClient) {
		String storageSystem = exportMask.getStorageDevice().toString();
		setVirtualArray(virtualArray = exportGroup.getVirtualArray());
		if (exportGroup.hasAltVirtualArray(storageSystem)) {
			setAltVirtualArray(URI.create(exportGroup.getAltVirtualArrays().get(storageSystem)));
		}
		setHasExistingVolumes(exportMask.hasAnyExistingVolumes());
		setExportGroup(exportGroup.getId());
		setExportGroupDisplay(exportGroup.forDisplay());
		setMaskName(exportMask.getMaskName());
		setMaskId(exportMask.getId());
		setVolumes(StringSetUtil.stringSetToUriList(exportMask.getVolumes().keySet()));
		Set<Initiator> initiators = ExportMaskUtils.getInitiatorsForExportMask(dbClient, exportMask, Transport.FC);
		NetworkScheduler.checkZoningMap(exportGroup, exportMask, initiators, dbClient);
		setZoningMap(exportMask.getZoningMap());
	}
	
	/**
	 * Converts a list of ExportMask to a list of NetworkZoningParam blocks containing
	 * the required attributes from the ExportMask.
	 * @param exportGroupURI -- URI of ExportGroup that is being operated on
	 * @param exportMaskURIs -- List of URIs for ExportMasks to be converted
	 * @param dbClient -- database handle
	 * @return list of NetworkZoningParam
	 * @throws WorkflowException if any mask is not active
	 */
	static public List<NetworkZoningParam> convertExportMasksToNetworkZoningParam(
			URI exportGroupURI, List<URI> exportMaskURIs, DbClient dbClient) {
		ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
		List<NetworkZoningParam> zoningParams = new ArrayList<NetworkZoningParam>();
		for (URI exportMaskURI : exportMaskURIs) {
			ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
			if (exportMask == null || exportMask.getInactive()) {
				throw WorkflowException.exceptions.workflowConstructionError(
						"ExportMask is null: " + exportMaskURI.toString());
			}
			NetworkZoningParam zoningParam = new NetworkZoningParam(exportGroup, exportMask, dbClient);
			zoningParams.add(zoningParam);
		}
		return zoningParams;
	}
	
	/**
	 * Generates a list of NetworkZoningParam objects from a map of export mask URI to a list of initiator URIs.
	 * Only the initiators in the exportMaskToInitiators map are retained from the ExportMask initiators.
	 * @param exportGroupURI
	 * @param exportMaskToInitiators
	 * @param dbClient
	 * @return
	 */
	static public List<NetworkZoningParam> convertExportMaskInitiatorMapsToNetworkZoningParam(
			URI exportGroupURI, Map<URI, List<URI>> exportMaskToInitiators, DbClient dbClient) {
		ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
		List<NetworkZoningParam> zoningParams = new ArrayList<NetworkZoningParam>();
		for (Map.Entry<URI, List<URI>> entry : exportMaskToInitiators.entrySet()) {
			ExportMask exportMask = dbClient.queryObject(ExportMask.class, entry.getKey());
			if (exportMask == null || exportMask.getInactive()) {
				throw WorkflowException.exceptions.workflowConstructionError(
						"ExportMask is null: " + entry.getKey().toString());
			}
			NetworkZoningParam zoningParam = new NetworkZoningParam(exportGroup, exportMask, dbClient);
			// Filter out entries in the zoning map not in the initiator list.
			// This is done by retaining all the initiators in the exportMaskToInitiators value.
			Set<String> retainedInitiators = StringSetUtil.uriListToSet(entry.getValue());
			zoningParam.getZoningMap().keySet().retainAll(retainedInitiators);
			// Add zoningParam to result
			zoningParams.add(zoningParam);
		}
		return zoningParams;
	}

	public StringSetMap getZoningMap() {
		return zoningMap;
	}
	public void setZoningMap(StringSetMap zoningMap) {
		this.zoningMap = zoningMap;
	}
	public boolean hasExistingVolumes() {
		return hasExistingVolumes;
	}
	public void setHasExistingVolumes(boolean hasExistingVolumes) {
		this.hasExistingVolumes = hasExistingVolumes;
	}
	public URI getVirtualArray() {
		return virtualArray;
	}
	public void setVirtualArray(URI virtualArray) {
		this.virtualArray = virtualArray;
	}
	public URI getAltVirtualArray() {
		return altVirtualArray;
	}
	public void setAltVirtualArray(URI altVirtualArray) {
		this.altVirtualArray = altVirtualArray;
	}

	public URI getExportGroupId() {
		return exportGroupId;
	}

	public void setExportGroup(URI exportGroupId) {
		this.exportGroupId = exportGroupId;
	}

	public String getMaskName() {
		return maskName;
	}

	public void setMaskName(String maskName) {
		this.maskName = maskName;
	}

	public URI getMaskId() {
		return maskId;
	}

	public void setMaskId(URI maskURI) {
		this.maskId = maskURI;
	}

	public String getExportGroupDisplay() {
		return exportGroupDisplay;
	}

	public void setExportGroupDisplay(String exportGroupDisplay) {
		this.exportGroupDisplay = exportGroupDisplay;
	}

	public List<URI> getVolumes() {
		return volumes;
	}

	public void setVolumes(List<URI> volumes) {
		this.volumes = volumes;
	}
	
}
