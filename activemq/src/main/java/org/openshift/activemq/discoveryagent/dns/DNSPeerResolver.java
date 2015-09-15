/**
 *  Copyright 2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.openshift.activemq.discoveryagent.dns;

import org.openshift.activemq.discoveryagent.PeerAddressResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DNSPeerResolver
 * <p/>
 * Resolves peer addresses using DNS to lookup the endpoints associated with the
 * specified service.
 */
public class DNSPeerResolver implements PeerAddressResolver {

    private final static Logger LOGGER = LoggerFactory.getLogger(DNSPeerResolver.class);

    private final String serviceName;
    private final DNSUtil dns;
    private String endpointName;
    private int servicePort = -1;

    /**
     * Create a new DNSPeerResolver.
     * 
     * @param serviceName the service name
     * @param servicePort the service port
     */
    public DNSPeerResolver(String serviceName, int servicePort) {
        this(serviceName);
        this.servicePort = servicePort;
    }

    /**
     * Create a new DNSPeerResolver.
     * 
     * @param serviceName the service name
     */
    public DNSPeerResolver(String serviceName) {
        this.dns = new DNSUtil();
        this.serviceName = serviceName;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String[] getPeerIPs() {
        init();
        if (endpointName == null) {
            return new String[0];
        }
        return dns.lookupIPs(endpointName);
    }

    @Override
    public int getServicePort() {
        init();
        return servicePort;
    }

    private synchronized void init() {
        if (endpointName != null) {
            return;
        }
        endpointName = dns.getEndpointNameForService(serviceName);
        LOGGER.info("Calculated endpoint name {} for service {}", endpointName, serviceName);
        if (endpointName == null) {
            // no endpoints
            LOGGER.warn("Could not get endpoint for service: {}.", serviceName);
            return;
        }
        if (servicePort < 1) {
            try {
                servicePort = Integer.valueOf(dns.getPortForService(endpointName));
            } catch (Exception e) {
                LOGGER.warn("Error retrieving service port.  61616 will be used.", e);
                // default to 61616
                servicePort = 61616;
            }
        }
    }
}
