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
    private String serviceName;

    @Property
    private int servicePort;

    private ServerFactory factory;
    private Server server;

    public void setFactory(ServerFactory factory) {
        this.factory = factory;
    }

    @Override
    public void start() throws Exception {
        int svcPort = getServicePort(getServiceName());
        if (factory != null) {
            server = factory.getServer(svcPort);
        } else {
            server = Utils.getServer(svcPort);
        }
        final String serverName = server.getClass().getSimpleName();
        if (log.isInfoEnabled()) {
            log.info(String.format("Starting %s on port %s for channel address: %s", serverName, svcPort, stack.getChannel().getAddress()));
        }
        server.start(stack.getChannel());
        if (log.isInfoEnabled()) {
            log.info(String.format("%s started.", serverName));
        }
    }

    @Override
    public void stop() {
        try {
            final String serverName = server.getClass().getSimpleName();
            if (log.isInfoEnabled()) {
                log.info(String.format("Stopping server: %s", serverName));
            }
            server.stop(stack.getChannel());
            if (log.isInfoEnabled()) {
                log.info(String.format("%s stopped.", serverName));
            }
        } finally {
            super.stop();
        }
    }

    /**
     * Reads all information from the given directory under clusterName.
     *
     * @return all data
     */
    protected synchronized List<PingData> readAll(String clusterName) {
        List<PingData> retval = new ArrayList<>();
        String svcName = getServiceName();
        Set<String> svcHosts = getServiceHosts(svcName);
        int svcPort = getServicePort(svcName);
        if (log.isDebugEnabled()) {
            log.debug(String.format("Reading service hosts %s on port [%s]", svcHosts, svcPort));
        }
        for (String svcHost : svcHosts) {
            try {
                PingData pingData = getPingData(svcHost, svcPort, clusterName);
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Adding PingData [%s]", pingData));
                }
                retval.add(pingData);
            } catch (Exception e) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Problem getting PingData for cluster [%s], service [%s], host [%s], port [%s]; encountered [%s: %s]",
                            clusterName, svcName, svcHost, svcPort, e.getClass().getName(), e.getMessage()));
                }
            }
        }
        return retval;
    }

    private String getServiceName() {
        String svcName = trimToNull(this.serviceName);
        if (svcName == null) {
            svcName = trimToNull(System.getenv("OPENSHIFT_DNS_PING_SERVICE_NAME"));
            if (svcName == null) {
                svcName = "ping";
            }
        }
        return svcName;
    }

    private int getServicePort(String svcName) {
        int svcPort = this.servicePort;
        if (svcPort < 1) {
            String env = trimToNull(System.getenv("OPENSHIFT_DNS_PING_SERVICE_PORT"));
            if (env != null) {
                try {
                    svcPort = Integer.parseInt(env);
                } catch (NumberFormatException nfe) {
                    if (log.isErrorEnabled()) {
                        log.error(String.format("OPENSHIFT_DNS_PING_SERVICE_PORT [%s] is not an integer: %s", env, nfe.getMessage()));
                    }
                }
            }
            if (svcPort < 1) {
                Integer dnsPort = execute(new GetServicePort(svcName), 3, 1000);
                if (dnsPort != null) {
                    svcPort = dnsPort.intValue();
                } else if (log.isWarnEnabled()) {
                    log.warn(String.format("No DNS SRV record found for service [%s]", svcName));
                }
                if (svcPort < 1) {
                    svcPort = 8888;
                }
            }
        }
        return svcPort;
    }

    private Set<String> getServiceHosts(String svcName) {
        Set<String> svcHosts = execute(new GetServiceHosts(svcName), 3, 1000);
        if (svcHosts == null) {
            svcHosts = Collections.emptySet();
            if (log.isWarnEnabled()) {
                log.warn(String.format("No matching hosts found for service [%s]; continuing...", svcName));
            }
        }
        return svcHosts;
    }

    private <V> V execute(DnsOperation<V> operation, int tries, long sleep) {
        V value = null;
        final int attempts = tries;
        Throwable lastFail = null;
        while (tries > 0) {
            tries--;
            try {
               value = operation.call();
               if (value != null) {
                   lastFail = null;
                   break;
               }
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
        if (lastFail != null && log.isDebugEnabled()) {
            String emsg = String.format("%s attempt(s) with a %sms sleep to execute DNS operation [%s] failed. Last failure was [%s: %s]",
                    attempts, sleep, operation.getClass().getSimpleName(),
                    (lastFail != null ? lastFail.getClass().getName() : "null"),
                    (lastFail != null ? lastFail.getMessage() : ""));
            log.debug(emsg);
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
        // prevent writing to file in jgroups 3.2.x
    }

    protected void write(List<PingData> list, String clustername) {
        // prevent writing to file in jgroups 3.6.x
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
