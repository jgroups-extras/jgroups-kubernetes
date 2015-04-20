/**
 *  Copyright 2014 Red Hat, Inc.
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

package org.openshift.ping.dns;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgroups.Address;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.FILE_PING;
import org.jgroups.protocols.PingData;
import org.openshift.ping.server.Server;
import org.openshift.ping.server.ServerFactory;
import org.openshift.ping.server.Utils;

@MBean(description = "DNS based discovery protocol")
public class DnsPing extends FILE_PING {

    public static final short OPENSHIFT_DNS_PING_ID = 2003;
    public static final short JGROUPS_DNS_PING_ID = 2004;
    static {
        ClassConfigurator.addProtocol(OPENSHIFT_DNS_PING_ID, DnsPing.class);
    }

    @Property
    private String service;

    @Property
    private String namespace;

    @Property
    private String domain;

    @Property
    private int serverPort;

    private ServerFactory factory;
    private Server server;

    public void setFactory(ServerFactory factory) {
        this.factory = factory;
    }

    private String getService() {
        if (service != null) {
            return service;
        } else {
            String env = trimToNull(System.getenv("OPENSHIFT_PING_SERVICE"));
            if (env != null) {
                return env;
            } else {
                return "ping";
            }
        }
    }

    public void setService(String service) {
        this.service = service;
    }

    private String getNamespace() {
        if (namespace != null) {
            return namespace;
        } else {
            String env = trimToNull(System.getenv("OPENSHIFT_PING_NAMESPACE"));
            if (env != null) {
                return env;
            } else {
                return "default";
            }
        }
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    private String getDomain() {
        if (domain != null) {
            return domain;
        } else {
            String env = trimToNull(System.getenv("OPENSHIFT_PING_DOMAIN"));
            if (env != null) {
                return env;
            } else {
                return "local";
            }
        }
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    private String getServiceName() {
        return new StringBuilder()
            .append(getService()).append('.')
            .append(getNamespace()).append('.')
            .append(getDomain())
        .toString();
    }

    private int getServerPort() {
        if (serverPort > 0) {
            return serverPort;
        } else {
            return 8888;
        }
    }

    @Override
    public void start() throws Exception {
        if (factory != null) {
            server = factory.getServer(getServerPort());
        } else {
            server = Utils.getServer(getServerPort());
        }
        final String serverName = server.getClass().getSimpleName();
        log.info(String.format("Starting server: %s, daemon port: %s, channel address: %s", serverName, getServerPort(), stack.getChannel().getAddress()));
        server.start(stack.getChannel());
        log.info(String.format("%s started.", serverName));
    }

    @Override
    public void stop() {
        try {
            final String serverName = server.getClass().getSimpleName();
            log.info(String.format("Stopping server: %s", serverName));
            server.stop(stack.getChannel());
            log.info(String.format("%s stopped.", serverName));
        } finally {
            super.stop();
        }
    }

    /**
     * Reads all information from the given directory under clustername
     *
     * @return all data
     */
    protected synchronized List<PingData> readAll(String clusterName) {
        List<PingData> retval = new ArrayList<>();
        String serviceName = getServiceName();
        Set<String> hostAddresses = getServiceHosts(serviceName);
        int servicePort = getServicePort(serviceName);
        for (String hostAddress : hostAddresses) {
            try {
                PingData pingData = getPingData(hostAddress, servicePort, clusterName);
                retval.add(pingData);
            } catch (Exception e) {
                log.error(String.format("Problem getting ping data for cluster [%s], service [%s], hostAddress [%s], servicePort [%s]; encountered [%s: %s]",
                        clusterName, serviceName, hostAddress, servicePort, e.getClass().getName(), e.getMessage()), e);
            }
        }
        return retval;
    }

    private Set<String> getServiceHosts(String serviceName) {
        return execute(new GetServiceHosts(serviceName), 100, 500);
    }

    private int getServicePort(String serviceName) {
        Integer value = execute(new GetServicePort(serviceName), 100, 500);
        if (value == null) {
            log.warn(String.format("No matching DNS SRV record found for service [%s]; defaulting to service port [%s]",
                    serviceName, getServerPort()));
            value = Integer.valueOf(getServerPort());
        }
        return value.intValue();
    }

    private <V> V execute(DnsOperation<V> operation, int tries, long sleep) {
        V value = null;
        final int attempts = tries;
        Throwable lastFail = null;
        while (tries > 0) {
            tries--;
            try {
               value = operation.call();
            } catch (Throwable fail) {
                lastFail = fail;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        if (lastFail != null) {
            String emsg = String.format("%s attempt(s) to execute DNS operation [%s] failed. Last failure was [%s: %s].",
                    attempts, operation.getClass().getSimpleName(),
                    (lastFail != null ? lastFail.getClass().getName() : "null"),
                    (lastFail != null ? lastFail.getMessage() : ""));
            log.error(emsg, lastFail);
        }
        return value;
    }

    private PingData getPingData(String host, int port, String clusterName) throws Exception {
        String url = String.format("http://%s:%s", host, port);
        PingData data = new PingData();
        Map<String, String> headers = Collections.singletonMap(Server.CLUSTER_NAME, clusterName);
        try (InputStream is = Utils.openStream(url, headers, 100, 500)) {
            data.readFrom(new DataInputStream(is));
        }
        return data;
    }

    @Override
    protected void createRootDir() {
        // empty on purpose to prevent dir from being created in the local file system
    }

    @Override
    protected void writeToFile(PingData data, String clustername) {
    }

    @Override
    protected void remove(String clustername, Address addr) {
    }

    private String trimToNull(String s) {
        if (s != null) {
            s = s.trim();
            if (s.length() == 0) {
                s = null;
            }
        }
        return s;
    }
}
