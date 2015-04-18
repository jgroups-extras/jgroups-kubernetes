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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

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
            server = factory.create(getServerPort(), stack.getChannel());
        } else {
            server = Utils.createServer(getServerPort(), stack.getChannel());
        }
        final String serverName = server.getClass().getSimpleName();
        log.info(String.format("Starting server: %s, daemon port: %s, channel address: %s", serverName, getServerPort(), stack.getChannel().getAddress()));
        server.start();
        log.info(String.format("%s started.", serverName));
    }

    @Override
    public void stop() {
        try {
            final String serverName = server.getClass().getSimpleName();
            log.info(String.format("Stopping server: %s", serverName));
            server.stop();
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
        int servicePort = getServicePort(serviceName);
        Set<String> hostAddresses = getServiceHosts(serviceName);
        for (String hostAddress : hostAddresses) {
            try {
                PingData pingData = getPingData(hostAddress, servicePort);
                retval.add(pingData);
            } catch (Exception e) {
                log.error(String.format("Problem getting ping data for cluster [%s], service [%s], hostAddress [%s], servicePort [%s]; encountered [%s: %s]",
                        clusterName, serviceName, hostAddress, servicePort, e.getClass().getName(), e.getMessage()), e);
            }
        }
        return retval;
    }

    private Set<String> getServiceHosts(String serviceName) {
        Set<String> serviceHosts = new LinkedHashSet<String>();
        try {
            InetAddress[] inetAddresses = InetAddress.getAllByName(serviceName);
            for (InetAddress inetAddress : inetAddresses) {
               serviceHosts.add(inetAddress.getHostAddress());
            }
        } catch (Exception e) {
            log.error(String.format("Problem getting service hosts by name [%s]; encountered [%s: %s]",
                    serviceName, e.getClass().getName(), e.getMessage()), e);
        }
        return serviceHosts;
    }

    private int getServicePort(String serviceName) {
        Set<DnsRecord> dnsRecords = getDnsRecords(serviceName);
        for (DnsRecord dnsRecord : dnsRecords) {
            if (serviceName.equals(dnsRecord.getHost())) {
                return dnsRecord.getPort();
            }
        }
        log.warn(String.format("No matching DNS SRV record found for service [%s]; defaulting to service port [%s]",
                serviceName, getServerPort()));
        return getServerPort();
    }

    private Set<DnsRecord> getDnsRecords(String serviceName) {
        Set<DnsRecord> dnsRecords = new TreeSet<DnsRecord>();
        try {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(serviceName, new String[]{"SRV"});
            NamingEnumeration<?> servers = attrs.get("SRV").getAll();
            while (servers.hasMore()) {
                DnsRecord record = DnsRecord.fromString((String)servers.next());
                dnsRecords.add(record);
            }
        } catch (Exception e) {
            log.error(String.format("Problem getting DNS SRV records for service [%s]; encountered [%s: %s]",
                    serviceName, e.getClass().getName(), e.getMessage()), e);
        }
        return dnsRecords;
    }

    private PingData getPingData(String host, int port) throws Exception {
        String url = String.format("http://%s:%s", host, port);
        PingData data = new PingData();
        try (InputStream is = Utils.openStream(url, 100, 500)) {
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
