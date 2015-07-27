/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.SRDF;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.google.common.base.Joiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ConnectivityCollectionRelationshipsProcessor extends StorageProcessor {
	private Logger _log = LoggerFactory
			.getLogger(ConnectivityCollectionRelationshipsProcessor.class);
	private static final String PROTOCOL_END_POINT = "ProtocolEndpoint";
	private static final String VOLUME = "Volume";
	private List<Object> args;
	private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
		try {
			@SuppressWarnings("unchecked")
			final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
			boolean volumeAdded = false;
			_dbClient = (DbClient) keyMap.get(Constants.dbClient);
			CIMObjectPath raGroupPath = getObjectPathfromCIMArgument(args);
			String ragGroupId = NativeGUIDGenerator
					.generateRAGroupNativeGuid(raGroupPath);
			_log.debug("RA Group Id : {}", ragGroupId);
			RemoteDirectorGroup rg = getRAGroupUriFromDB(_dbClient, ragGroupId);
			if (null == rg) {
				_log.info("RA Group Not found : {}", ragGroupId);
				return;
			}
			URI raGroupUri = rg.getId();

			@SuppressWarnings("unchecked")
			Map<String, URI> rAGroupMap = (Map<String, URI>) keyMap
					.get(Constants.RAGROUP);

			Set<String> volumeNativeGuids = new StringSet();
			while (it.hasNext()) {
				CIMObjectPath connCollectionRelationPaths = it.next();

				if (connCollectionRelationPaths.toString().contains(
						PROTOCOL_END_POINT)) {
					String endPointId = connCollectionRelationPaths
							.getKey(Constants.NAME).getValue().toString();
					_log.info("End Point Added {}", connCollectionRelationPaths);
					addPath(keyMap, Constants.ENDPOINTS_RAGROUP,
							connCollectionRelationPaths);
					rAGroupMap.put(endPointId, raGroupUri);
				} else if (connCollectionRelationPaths.toString().contains(
						VOLUME)) {
					String volumeNativeGuid = getVolumeNativeGuid(connCollectionRelationPaths);
					if (!volumeAdded
							&& !rAGroupMap.containsKey(volumeNativeGuid)) {
						volumeAdded = true;
						_log.info("Volume Added {}",
								connCollectionRelationPaths);
						addPath(keyMap, Constants.VOLUME_RAGROUP,
								connCollectionRelationPaths);
						rAGroupMap.put(volumeNativeGuid, raGroupUri);
					} else {
						_log.info("Volume {} is part of multiple RA Groups",
								volumeNativeGuid);
					}
					volumeNativeGuids.add(volumeNativeGuid);
				}
			}
			RemoteDirectorGroup remoteGroup = _dbClient.queryObject(
					RemoteDirectorGroup.class, raGroupUri);
			// if no volumes, then by default this group supports both sync and
			// async
			if (!volumeAdded) {
				remoteGroup.setSupportedCopyMode(SupportedCopyModes.ALL
						.toString());
			}

			if (null == remoteGroup.getVolumes()
					|| remoteGroup.getVolumes().isEmpty()) {
				remoteGroup.setVolumes(new StringSet(volumeNativeGuids));
			} else {
				_log.debug("Existing Volumes {}",
						Joiner.on("\t").join(remoteGroup.getVolumes()));
				_log.debug("New Volumes {}",
						Joiner.on("\t").join(volumeNativeGuids));
				remoteGroup.getVolumes().replace(volumeNativeGuids);
				_log.debug("Updated Volumes {}",
						Joiner.on("\t").join(remoteGroup.getVolumes()));
			}
			_dbClient.persistObject(remoteGroup);
		}  catch (Exception e) {
            _log.error("Exception occurred while processing remote connectivity information.", e);
        }
        
    }

	@Override
	protected void setPrerequisiteObjects(List<Object> inputArgs)
			throws BaseCollectionException {
		args = inputArgs;
	}

}
