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

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.command.DiscoveryEvent;
import org.apache.activemq.thread.Scheduler;
import org.apache.activemq.transport.discovery.DiscoveryAgent;
import org.apache.activemq.transport.discovery.DiscoveryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DNSDiscoveryAgent
 * <p/>
 * Provides support for a discovery agent that queries DNS for services
 * implemented using an openwire (tcp) transport. The URI takes the form:
 * 
 * <pre>
 * dns://<serviceName>:<servicePort>/?queryInterval=30&transportType=tcp
 * </pre>
 * 
 * <code>serviceName</code> is required and is the DNS name of the service. For
 * OpenShift, this may be the simple name of the service, as OpenShift
 * configures the pod with search suffixes (e.g. project.svc.cluster.local).
 * <code>servicePort</code> is optional. If not specified, the agent will query
 * DNS for SRV records to determine the port on which the services are running.
 * <code>queryInterval</code> is the period, in seconds, at which DNS is polled
 * for records; the default is 30s. <code>transportType</code> is the type of
 * transport; the default is <code>tcp</code>.
 */
public class DNSDiscoveryAgent implements DiscoveryAgent {

    private final static Logger LOGGER = LoggerFactory.getLogger(DNSDiscoveryAgent.class);

    /** The query interval in seconds. */
    private long queryInterval = 30;
    /** The name of the service providing an openwire (tcp) transport. */
    private String serviceName;
    /** The port on which the service(s) are running. */
    private int servicePort;
    /** The transportType, e.g. tcp, amqp, etc., defaults to tcp. */
    private String transportType = "tcp";
    private long minConnectTime = 1000;
    private long initialReconnectDelay = minConnectTime;
    private long maxReconnectDelay = 16000;
    private int maxReconnectAttempts = 4;
    /** The periodic poller of DNS information for the service. */
    private Scheduler dnsPoller;
    private ConcurrentMap<String, DNSDiscoveryEvent> services = new ConcurrentHashMap<String, DNSDiscoveryAgent.DNSDiscoveryEvent>();
    private DiscoveryListener listener;

    /**
     * Create a new DNSDiscoveryAgent.
     * 
     * @param host the service host name
     * @param port the service port
     */
    public DNSDiscoveryAgent(String host, int port) throws IOException {
        if (host == null || host.length() == 0) {
            throw new IOException("Invalid host name: " + host);
        }
        serviceName = host;
        servicePort = port;
    }

    @Override
    public synchronized void start() throws Exception {
        LOGGER.info("Starting DNS discovery agent for service {} on port {} for transport type {}", serviceName,
                servicePort, transportType);
        dnsPoller = new Scheduler("DNS discovery agent Scheduler: " + serviceName);
        dnsPoller.start();
        dnsPoller.executePeriodically(new DNSQueryTask(), TimeUnit.SECONDS.toMillis(queryInterval));
    }

    @Override
    public synchronized void stop() throws Exception {
        LOGGER.info("Stopping DNS discovery agent for service {} on port {} for transport type {}", serviceName,
                servicePort, transportType);
        if (dnsPoller != null) {
            dnsPoller.stop();
            dnsPoller = null;
        }
    }

    @Override
    public void setDiscoveryListener(DiscoveryListener listener) {
        this.listener = listener;
    }

    @Override
    public void registerService(String name) throws IOException {
    }

    @Override
    public void serviceFailed(DiscoveryEvent event) throws IOException {
        final DNSDiscoveryEvent dnsEvent = (DNSDiscoveryEvent) event;
        dnsEvent.fail();
    }

    /**
     * Get the queryInterval.
     * 
     * @return the queryInterval.
     */
    public long getQueryInterval() {
        return queryInterval;
    }

    /**
     * Set the queryInterval.
     * 
     * @param queryInterval The queryInterval to set.
     */
    public void setQueryInterval(long queryInterval) {
        this.queryInterval = queryInterval;
    }

    /**
     * Get the transportType.
     * 
     * @return the transportType.
     */
    public String getTransportType() {
        return transportType;
    }

    /**
     * Set the transportType.
     * 
     * @param transportType The transportType to set.
     */
    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    private final class DNSDiscoveryEvent extends DiscoveryEvent {

        private int connectFailures;
        private long reconnectDelay = 1000;
        private long connectTime = System.currentTimeMillis();
        private boolean failed;

        public DNSDiscoveryEvent(String transportType, String ip, int port) {
            super(String.format("%s://%s:%d", transportType, ip, port));
        }

        @Override
        public String toString() {
            return "[" + serviceName + ", failed:" + failed + ", connectionFailures:" + connectFailures + "]";
        }

        private synchronized void present() {
            if (failed) {
                return;
            }
            connectFailures = 0;
            reconnectDelay = initialReconnectDelay;
        }

        private synchronized void fail() {
            if (failed) {
                return;
            }
            if (!services.containsValue(this)) {
                // no longer tracking this discovery event
                return;
            }
            final long retryDelay;
            if (connectTime + minConnectTime > System.currentTimeMillis()) {
                connectFailures++;
                retryDelay = reconnectDelay;
            } else {
                retryDelay = minConnectTime;
            }

            failed = true;
            listener.onServiceRemove(this);

            if (maxReconnectAttempts > 0 && connectFailures >= maxReconnectAttempts) {
                // This service will forever be exempted from this broker's mesh
                LOGGER.warn("Reconnect attempts exceeded after {} tries.  Reconnecting has been disabled for: {}",
                        maxReconnectAttempts, this);
                return;
            }

            dnsPoller.executeAfterDelay(new Runnable() {
                @Override
                public void run() {
                    reconnect();
                }
            }, retryDelay);
        }

        private synchronized void reconnect() {
            if (!services.containsValue(this)) {
                // no longer tracking this discovery event
                return;
            }

            // Exponential increment of reconnect delay.
            reconnectDelay *= 2;
            if (reconnectDelay > maxReconnectDelay) {
                reconnectDelay = maxReconnectDelay;
            }
            connectTime = System.currentTimeMillis();
            failed = false;

            listener.onServiceAdd(this);
        }
    }

    /**
     * DNSQueryTask
     */
    private class DNSQueryTask implements Runnable {

        private String endpointName;
        private DNSUtil dns = new DNSUtil();

        /**
         * Create a new DNSQueryTask.
         */
        public DNSQueryTask() {
        }

        @Override
        public void run() {
            try {
                if (endpointName == null) {
                    endpointName = dns.getEndpointNameForService(serviceName);
                    LOGGER.info("Calculated endpoint name {} for service {}", endpointName, serviceName);
                }
                if (endpointName == null) {
                    // no endpoints
                    LOGGER.warn("Could not get endpoint for service: {}.", serviceName);
                    // unregister services
                    Set<DNSDiscoveryEvent> events;
                    synchronized (services) {
                        events = new HashSet<DNSDiscoveryEvent>(services.values());
                        services.clear();
                    }
                    for (DNSDiscoveryEvent event : events) {
                        if (event != null) {
                            LOGGER.info("Removing service: {}", event);
                            listener.onServiceRemove(event);
                        }
                    }
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
                synchronized (services) {
                    final Set<String> endpoints = new HashSet<String>(Arrays.asList(dns.lookupIPs(endpointName)));
                    final Set<String> removed = new HashSet<String>(services.keySet());
                    final Set<String> added = new HashSet<String>(endpoints);
                    removed.removeAll(endpoints);
                    for (String service : removed) {
                        final DNSDiscoveryEvent event = services.remove(service);
                        if (event != null) {
                            LOGGER.info("Removing service: {}", event);
                            listener.onServiceRemove(event);
                        }
                    }
                    for (Map.Entry<String, DNSDiscoveryEvent> entry : services.entrySet()) {
                        added.remove(entry.getKey());
                        entry.getValue().present();
                    }
                    for (String service : added) {
                        if (service.equals(InetAddress.getLocalHost().getHostAddress())) {
                            // skip ourself
                            continue;
                        }
                        final DNSDiscoveryEvent event = new DNSDiscoveryEvent(transportType, service, servicePort);
                        services.put(service, event);
                        LOGGER.info("Adding service: {}", event);
                        listener.onServiceAdd(event);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error polling DNS", e);
            }
        }

    }

}
