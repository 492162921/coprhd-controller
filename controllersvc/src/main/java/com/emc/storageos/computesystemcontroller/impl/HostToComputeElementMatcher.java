/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.UCSServiceProfile;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

public final class HostToComputeElementMatcher {

    private final static Logger _log = LoggerFactory.getLogger(HostToComputeElementMatcher.class);
    private static StringBuffer failureMessages;
    private static DbClient dbClient;
    private static Map<URI,Host> hostMap = new HashMap<>();
    private static Map<URI,ComputeElement> computeElementMap = new HashMap<>();
    private static Map<URI,UCSServiceProfile> serviceProfileMap = new HashMap<>();
    private static boolean allHostsLoaded = false;

    private final static String UUID_REGEX = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private final static Pattern UUID_PATTERN = Pattern.compile(UUID_REGEX,Pattern.CASE_INSENSITIVE);

    private HostToComputeElementMatcher(){}

    public static void matchHostToComputeElements(DbClient _dbClient, URI hostId) {
        Collection<URI> hostIds = Arrays.asList(hostId);  // single host
        Collection<URI> computeElementIds = dbClient.queryByType(ComputeElement.class, true); // all active
        Collection<URI> serviceProfileIds = dbClient.queryByType(UCSServiceProfile.class, true); // all active
        matchHostsToComputeElements(_dbClient,hostIds,computeElementIds,serviceProfileIds);
    }

    public static void matchHostsToComputeElements(DbClient _dbClient, Collection<URI> hostIds) {
        Collection<URI> computeElementIds = dbClient.queryByType(ComputeElement.class, true); // all active
        Collection<URI> serviceProfileIds = dbClient.queryByType(UCSServiceProfile.class, true); // all active
        matchHostsToComputeElements(_dbClient,hostIds,computeElementIds,serviceProfileIds);
    }

    public static void matchUcsComputeElements(DbClient _dbClient, URI computeSystemId) {
        Collection<URI> hostIds = dbClient.queryByType(Host.class, true); // all active hosts
        allHostsLoaded = true;

        URIQueryResultList computeElementIds = new URIQueryResultList(); // CEs for this UCS
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemComputeElemetsConstraint(computeSystemId), computeElementIds);

        URIQueryResultList serviceProfileIds = new URIQueryResultList(); // SPs for this UCS
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                getComputeSystemServiceProfilesConstraint(computeSystemId), serviceProfileIds);

         matchHostsToComputeElements(_dbClient,hostIds,computeElementIds,serviceProfileIds);
    }    

    private static void matchHostsToComputeElements(DbClient _dbClient,Collection<URI> hostIds,
            Collection<URI> computeElementIds,Collection<URI> serviceProfileIds) {

        dbClient = _dbClient;                              // set our client
        failureMessages = new StringBuffer();

        load(hostIds,computeElementIds,serviceProfileIds); // load hosts, computeElements &SPs
        matchHostsToBladesAndSPs();                        // find hosts & blades whose UUIDs match
        catchDuplicateMatches();                           // validate matches (check for duplicates)
        updateDb();                                        // persist changed Hosts & ServiceProfiles

        // after correcting all associations possible, cause discovery failure with message
        if (failureMessages.length() > 0) {
            throw ComputeSystemControllerException.exceptions.hostMatcherError(failureMessages.toString());
        }
    }

    private static void load(Collection<URI> hostIds, Collection<URI> computeElementIds, Collection<URI> serviceProfileIds) {

        Collection<Host> allHosts = dbClient.queryObjectFields(Host.class,
                Arrays.asList("uuid", "computeElement", "registrationStatus", "hostName","serviceProfile","label"),
                getFullyImplementedCollection(hostIds));

        Collection<ComputeElement> allComputeElements =
                dbClient.queryObjectFields(ComputeElement.class,
                        Arrays.asList("uuid", "registrationStatus", "dn", "available","label"),
                        getFullyImplementedCollection(computeElementIds));

        Collection<UCSServiceProfile> allUCSServiceProfiles =
                dbClient.queryObjectFields(UCSServiceProfile.class,
                        Arrays.asList("uuid", "registrationStatus", "dn", "label"),
                        getFullyImplementedCollection(serviceProfileIds));

        hostMap = makeUriMap(allHosts);
        computeElementMap = makeUriMap(allComputeElements);
        serviceProfileMap = makeUriMap(allUCSServiceProfiles);
    }

    private static void matchHostsToBladesAndSPs() {

        // lookup map (to find CEs by their UUID)
        Map<String,ComputeElement> ceMap = new HashMap<>();
        for(ComputeElement ce : computeElementMap.values()) {
            if (isValidUuid(ce.getUuid())) {
                ceMap.put(ce.getUuid(),ce);
            }
        }

        // lookup map (to find SPs by their UUID)
        Map<String,UCSServiceProfile> spMap = new HashMap<>();
        for(UCSServiceProfile sp : serviceProfileMap.values()) {
            if (isValidUuid(sp.getUuid())) {
                spMap.put(sp.getUuid(),sp);
            }
        }

        for (Host host: hostMap.values()) {
            _log.info("matching host " + info(host));

            // clear blade & SP associations for hosts that are unregistered or have bad UUIDs
            if(isUnregistered(host) || !hasValidUuid(host)) {
                _log.info("skipping host (unregistered or bad UUID); " + info(host));
                clearHostAssociations(host);
                continue;  // next host
            }

            // find matching blade & SP
            ComputeElement ce = getMatchingComputeElement(host,ceMap);
            UCSServiceProfile sp = getMatchingServiceProfile(host,spMap);

            // update Host & ServiceProfile
            if ((ce != null) & (sp != null)) {
                _log.info("matched host to SP & CE " + info(host) + ", " + info(sp) + ", " + info(ce));
                setHostAssociations(host,ce,sp);
            } else {
                _log.info("no match for host " + info(host));
                clearHostAssociations(host);  // clear associations if no match
            }
        }
    }

    private static void catchDuplicateMatches() {
        // safety checks to prevent DL/DU

        if(!allHostsLoaded) {
            return; // only complete if we loaded all hosts
        }

        Map<URI,URI> ceToHostMap = new HashMap<>();
        Map<URI,URI> spToHostMap = new HashMap<>();

        for(Host host : hostMap.values() ){

            if(!NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                if(!ceToHostMap.containsKey(host.getComputeElement())) {
                    ceToHostMap.put(host.getComputeElement(),host.getId());
                } else {
                    String msg = "The hosts " + info(host) + " and " +
                            info(hostMap.get(ceToHostMap.get(host.getComputeElement()))) +
                            " will not be associated to a ComputeElement since they both match the same one: " +
                            info(computeElementMap.get(host.getComputeElement()));
                    failureMessages.append(msg);
                    _log.warn(msg);
                    clearHostAssociations(host);
                    clearHostAssociations(hostMap.get(ceToHostMap.get(host.getComputeElement())));
                }
            }

            if(!NullColumnValueGetter.isNullURI(host.getServiceProfile())) {
                if(!spToHostMap.containsKey(host.getServiceProfile())) {
                    spToHostMap.put(host.getServiceProfile(),host.getId());
                } else {
                    String msg = "The hosts " + info(host) + " and " +
                            info(hostMap.get(spToHostMap.get(host.getServiceProfile()))) +
                            " will not be associated to a UCS ServiceProfile since they both match the same one: " +
                            info(serviceProfileMap.get(host.getServiceProfile()));
                    failureMessages.append(msg);
                    _log.warn(msg);
                    clearHostAssociations(host);
                    clearHostAssociations(hostMap.get(spToHostMap.get(host.getServiceProfile())));
                }
            }
        }
    }

    private static void updateDb() {
        List<Host> hostsToUpdate = new ArrayList<>();
        for(Host host : hostMap.values()) {
            if(host.isChanged("computeElement") || host.isChanged("serviceProfile")) {
                hostsToUpdate.add(host);
            }
        }
        dbClient.updateObject(hostsToUpdate);

        List<UCSServiceProfile> spsToUpdate = new ArrayList<>();
        for(UCSServiceProfile sp : serviceProfileMap.values()) {
            if(sp.isChanged("host")) {
                spsToUpdate.add(sp);
            }
        }
        dbClient.updateObject(spsToUpdate);
    }

    private static ComputeElement getMatchingComputeElement(Host host, Map<String, ComputeElement> ceMap) {

        if(!isValidUuid(host.getUuid())) {
            return null;
        }

        // check for matching UUID
        String uuid = host.getUuid();
        ComputeElement ceWithSameUuid = null;
        if (ceMap.containsKey(uuid) &&
                hostNameMatches(ceMap.get(uuid).getDn(),host) &&
                !isUnregistered(ceMap.get(uuid))) {
            ceWithSameUuid = ceMap.get(uuid);
        }

        // check for matching UUID in mixed-endian format
        String uuidReversed = reverseUuidBytes(host.getUuid());
        ComputeElement ceWithReversedUuid = null;
        if (ceMap.containsKey(uuidReversed) &&
                hostNameMatches(ceMap.get(uuidReversed).getDn(),host) &&
                !isUnregistered(ceMap.get(uuidReversed))) {
            ceWithReversedUuid = ceMap.get(uuidReversed);
        }

        if( (ceWithSameUuid != null) && (ceWithReversedUuid != null)) {
            String errMsg = "Host match failed for ComputeElement because host " +
                    info(host) + " matches multiple blades " + info(ceWithSameUuid) +
                    " and " + info(ceWithReversedUuid);
            _log.error(errMsg);
            failureMessages.append(errMsg);
            return null;
        }

        return ceWithSameUuid != null ? ceWithSameUuid : ceWithReversedUuid;
    }

    private static UCSServiceProfile getMatchingServiceProfile(Host host, Map<String, UCSServiceProfile> spMap) {

        if(!isValidUuid(host.getUuid())) {
            return null;
        }

        // check for matching UUID
        String uuid = host.getUuid();
        UCSServiceProfile spWithSameUuid = null;
        if (spMap.containsKey(uuid) &&
                hostNameMatches(spMap.get(uuid).getDn(),host) &&
                !isUnregistered(spMap.get(uuid))) {
            spWithSameUuid = spMap.get(uuid);
        }

        // check for matching UUID in mixed-endian format
        String uuidReversed = reverseUuidBytes(host.getUuid());
        UCSServiceProfile spWithReversedUuid = null;
        if (spMap.containsKey(uuidReversed) &&
                hostNameMatches(spMap.get(uuidReversed).getDn(),host) &&
                !isUnregistered(spMap.get(uuidReversed))) {
            spWithReversedUuid = spMap.get(uuidReversed);
        }

        if( (spWithSameUuid != null) && (spWithReversedUuid != null)) {
            String errMsg = "Host match failed for UCS Service Profile because host " +
                    info(host) + " matches multiple Service Profiles " + info(spWithSameUuid) +
                    " and " + info(spWithReversedUuid);
            _log.error(errMsg);
            failureMessages.append(errMsg);
            return null;
        }

        return spWithSameUuid != null ? spWithSameUuid : spWithReversedUuid;
    }

    private static void setHostAssociations(Host hostIn, ComputeElement ceIn, UCSServiceProfile spIn) {

        Host host = hostMap.get(hostIn.getId());

        if( (host.getComputeElement() == null) ||
                (!host.getComputeElement().equals(ceIn.getId()))) {
            host.setComputeElement(ceIn.getId());  // set new CE for host
        }

        if( (host.getServiceProfile() == null) ||
                (!host.getServiceProfile().equals(spIn.getId()))) {
            host.setServiceProfile(spIn.getId());  // set new SP for host
        }

        if(host.isChanged("computeElement") || host.isChanged("serviceProfile")) {
            hostMap.put(host.getId(), host);
        }

        UCSServiceProfile sp = serviceProfileMap.get(spIn.getId());

        if( (sp.getHost() == null) ||
                (!sp.getHost().equals(host.getId()))) {
            sp.setHost(host.getId());  // set new host in SP
            serviceProfileMap.put(sp.getId(), sp);
        }
    }

    private static void clearHostAssociations(Host host) {
        Host h = hostMap.get(host.getId());

        if(!NullColumnValueGetter.isNullURI(h.getComputeElement())) {
            h.setComputeElement(NullColumnValueGetter.getNullURI());
        }

        if(!NullColumnValueGetter.isNullURI(h.getServiceProfile())) {
            h.setServiceProfile(NullColumnValueGetter.getNullURI());
        }

        if(host.isChanged("computeElement") || host.isChanged("serviceProfile")) {
            hostMap.put(host.getId(), host);
        }

        // clear reference from SPs back to this Host
        for(UCSServiceProfile sp : serviceProfileMap.values()) {
            if( (sp.getHost() != null) && sp.getHost().equals(host.getId())) {
                sp.setHost(NullColumnValueGetter.getNullURI());
                serviceProfileMap.put(sp.getId(),sp);
            }
        }
    }

    private static boolean hasValidUuid(Host h) {
        return isValidUuid(h.getUuid());
    }

    private static boolean isValidUuid(String uuid) {
        if ( uuid == null ) {
            return false;
        }
        return UUID_PATTERN.matcher(uuid).matches();
    }

    private static <T> Collection<T> getFullyImplementedCollection(Collection<T> collectionIn) {
        // Convert objects (like URIQueryResultList) that only implement iterator to
        // fully implemented Collection
        Collection<T> collectionOut = new ArrayList<>();
        Iterator<T> iter = collectionIn.iterator();
        while (iter.hasNext()) {
            collectionOut.add(iter.next());
        }
        return collectionOut;
    }

    private static String reverseUuidBytes(String uuid) {
    /**
     * Older hosts report UUID in Big-Endian or "network-byte-order" (Most Significant Byte first)
     *     e.g.: {00112233-4455-6677-8899-AABBCCDDEEFF}
     * Newer Hosts' BIOSs supporting SMBIOS 2.6 or later report UUID in Little-Endian or "wire-format", where
     *   first 3 parts are in byte revered order (aka: 'mixed-endian')
     *     e.g.: {33221100-5544-7766-8899-AABBCCDDEEFF}
     **/

        // reverse bytes
        UUID uuidObj = UUID.fromString(uuid);
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuidObj.getMostSignificantBits());
        bb.putLong(uuidObj.getLeastSignificantBits());
        byte[] reorderedUuid = new byte[16];
        reorderedUuid[0] = bb.get(3); // reverse bytes in 1st part
        reorderedUuid[1] = bb.get(2);
        reorderedUuid[2] = bb.get(1);
        reorderedUuid[3] = bb.get(0);
        reorderedUuid[4] = bb.get(5); // reverse bytes in 2nd part
        reorderedUuid[5] = bb.get(4);
        reorderedUuid[6] = bb.get(7); // reverse bytes in 3rd part
        reorderedUuid[7] = bb.get(6);
        for(int byteIndex = 8; byteIndex < 16; byteIndex++ ) {
            reorderedUuid[byteIndex] = bb.get(byteIndex); // copy 4th & 5th parts unchanged
        }
        bb = ByteBuffer.wrap(reorderedUuid);
        UUID uuidNew = new UUID(bb.getLong(), bb.getLong());
        return uuidNew.toString();
    }

    private static <T extends DataObject> Map<URI,T> makeUriMap(Collection<T> c) {
        Map<URI,T> map = new HashMap<>();
        for(T dataObj : c) {
            map.put(dataObj.getId(), dataObj);
        }
        return map;
    }

    private static String info(Host h) {
        return h == null ? "" :
            (h.getLabel() != null ? "'" + h.getLabel() + "' " : "") +
            "(" + h.getId() + ")" +
            (h.getUuid() != null ? " [" + h.getUuid() + "]" : "");
    }

    private static String info(ComputeElement ce) {
        return ce == null ? "" :
            (ce.getLabel() != null ? "'" + ce.getLabel() + "' " : "") +
            "(" + ce.getId() + ")" +
            (ce.getUuid() != null ? " [" + ce.getUuid() + "]" : "");
    }

    private static String info(UCSServiceProfile sp) {
        return sp == null ? "" :
            (sp.getLabel() != null ? "'" + sp.getLabel() + "' " : "") +
            "(" + sp.getId() + ")" +
            (sp.getUuid() != null ? " [" + sp.getUuid() + "]" : "");
    }

    private static boolean isUnregistered(DiscoveredSystemObject o) {
        return RegistrationStatus.UNREGISTERED.name().equals(o.getRegistrationStatus());
    }

    private static boolean hostNameMatches(String dn, Host h) {
        // dn for CE & SP should end with Host's hostName
        return (dn != null) && (h.getHostName() != null) &&
                !h.getHostName().isEmpty() && dn.endsWith(h.getHostName());
    }
}
