
package org.jgroups.protocols.kubernetes;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.Discovery;
import org.jgroups.protocols.PingData;
import org.jgroups.protocols.PingHeader;
import org.jgroups.protocols.TP;
import org.jgroups.protocols.kubernetes.stream.CertificateStreamProvider;
import org.jgroups.protocols.kubernetes.stream.InsecureStreamProvider;
import org.jgroups.protocols.kubernetes.stream.StreamProvider;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.NameCache;
import org.jgroups.util.Responses;

import java.util.*;
import java.util.stream.Collectors;

import static org.jgroups.protocols.kubernetes.Utils.readFileToString;

/**
 * Kubernetes based discovery protocol. Uses the Kubernetes master to fetch the IP addresses of all pods that have
 * been created, then pings each pods separately. The ports are defined by bind_port in TP plus port_range.
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author Sebastian Łaskawiec
 * @author Bela Ban
 */
@MBean(description="Kubernetes based discovery protocol")
public class KUBE_PING extends Discovery {
    protected static final short KUBERNETES_PING_ID=2017;


    static {
        ClassConfigurator.addProtocol(KUBERNETES_PING_ID, KUBE_PING.class);
    }

    @Property(description="Number of additional ports to be probed for membership. A port_range of 0 does not " +
      "probe additional ports. Example: initial_hosts=A[7800] port_range=0 probes A:7800, port_range=1 probes " +
      "A:7800 and A:7801")
    protected int    port_range=1;

    @Property(description="Max time (in millis) to wait for a connection to the Kubernetes server. If exceeded, " +
      "an exception will be thrown", systemProperty="KUBERNETES_CONNECT_TIMEOUT")
    protected int    connectTimeout=5000;

    @Property(description="Max time (in millis) to wait for a response from the Kubernetes server",
      systemProperty="KUBERNETES_READ_TIMEOUT")
    protected int    readTimeout=30000;

    @Property(description="Max number of attempts to send discovery requests", systemProperty="KUBERNETES_OPERATION_ATTEMPTS")
    protected int    operationAttempts=3;

    @Property(description="Time (in millis) between operation attempts", systemProperty="KUBERNETES_OPERATION_SLEEP")
    protected long   operationSleep=1000;

    @Property(description="https (default) or http. Used to send the initial discovery request to the Kubernetes server",
      systemProperty="KUBERNETES_MASTER_PROTOCOL")
    protected String  masterProtocol="https";

    @Property(description="The URL of the Kubernetes server", systemProperty="KUBERNETES_SERVICE_HOST")
    protected String  masterHost;

    @Property(description="The port on which the Kubernetes server is listening", systemProperty="KUBERNETES_SERVICE_PORT")
    protected int     masterPort;

    @Property(description="The version of the protocol to the Kubernetes server", systemProperty="KUBERNETES_API_VERSION")
    protected String  apiVersion="v1";

    @Property(description="namespace", systemProperty="KUBERNETES_NAMESPACE")
    protected String  namespace="default";

    @Property(description="The labels to use in the discovery request to the Kubernetes server",
      systemProperty="KUBERNETES_LABELS")
    protected String  labels;

    @Property(description="Certificate to access the Kubernetes server", systemProperty="KUBERNETES_CLIENT_CERTIFICATE_FILE")
    protected String  clientCertFile;

    @Property(description="Client key file (store)", systemProperty="KUBERNETES_CLIENT_KEY_FILE")
    protected String  clientKeyFile;

    @Property(description="The password to access the client key store", systemProperty="KUBERNETES_CLIENT_KEY_PASSWORD")
    protected String  clientKeyPassword;

    @Property(description="The algorithm used by the client", systemProperty="KUBERNETES_CLIENT_KEY_ALGO")
    protected String  clientKeyAlgo="RSA";

    @Property(description="Client CA certificate", systemProperty="KUBERNETES_CA_CERTIFICATE_FILE")
    protected String  caCertFile;

    @Property(description="Token file", systemProperty="SA_TOKEN_FILE")
    protected String  saTokenFile="/var/run/secrets/kubernetes.io/serviceaccount/token";

    @Property(description="Dumps all discovery requests and responses to the Kubernetes server to stdout when true")
    protected boolean dump_requests;

    protected Client  client;

    protected int     tp_bind_port;

    public boolean isDynamic() {
        return false; // bind_port in the transport needs to be fixed (cannot be 0)
    }

    public void setMasterHost(String masterMost) {
        this.masterHost=masterMost;
    }

    public void setMasterPort(int masterPort) {
        this.masterPort=masterPort;
    }

    public void setNamespace(String namespace) {
        this.namespace=namespace;
    }

    protected boolean isClusteringEnabled() {
        return namespace != null;
    }


    public void init() throws Exception {
        super.init();

        TP transport=getTransport();
        tp_bind_port=transport.getBindPort();
        if(tp_bind_port <= 0)
            throw new IllegalArgumentException(String.format("%s only works with  %s.bind_port > 0",
                                                             KUBE_PING.class.getSimpleName(), transport.getClass().getSimpleName()));

        if(namespace == null) {
            log.warn("namespace not set; clustering disabled");
            return; // no further initialization necessary
        }
        log.info("namespace %s set; clustering enabled", namespace);
        Map<String,String> headers=new HashMap<>();
        StreamProvider streamProvider;
        if(clientCertFile != null) {
            if(masterProtocol == null)
                masterProtocol="http";
            streamProvider=new CertificateStreamProvider(clientCertFile, clientKeyFile, clientKeyPassword, clientKeyAlgo, caCertFile);
        }
        else {
            String saToken=readFileToString(saTokenFile);
            if(saToken != null) {
                // curl -k -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
                // https://172.30.0.2:443/api/v1/namespaces/dward/pods?labelSelector=application%3Deap-app
                headers.put("Authorization", "Bearer " + saToken);
            }
            streamProvider=new InsecureStreamProvider();
        }
        String url=String.format("%s://%s:%s/api/%s", masterProtocol, masterHost, masterPort, apiVersion);
        client=new Client(url, headers, connectTimeout, readTimeout, operationAttempts, operationSleep, streamProvider, log);
        log.debug("KubePING configuration: " + toString());
    }


    @Override public void destroy() {
        client=null;
        super.destroy();
    }

    public void findMembers(List<Address> members, boolean initial_discovery, Responses responses) {
        List<String>          hosts=readAll();
        List<PhysicalAddress> cluster_members=new ArrayList<>(hosts != null? hosts.size() : 16);
        PhysicalAddress       physical_addr=null;
        PingData              data=null;

        if(!use_ip_addrs || !initial_discovery) {
            physical_addr=(PhysicalAddress)down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));

            // https://issues.jboss.org/browse/JGRP-1670
            data=new PingData(local_addr, false, NameCache.get(local_addr), physical_addr);
            if(members != null && members.size() <= max_members_in_discovery_request)
                data.mbrs(members);
        }

        if(hosts != null) {
            if(log.isTraceEnabled())
                log.trace("%s: hosts fetched from Kubernetes: %s", local_addr, hosts);
            for(String host: hosts) {
                for(int i=0; i <= port_range; i++) {
                    try {
                        IpAddress addr=new IpAddress(host, tp_bind_port + i);
                        if(!cluster_members.contains(addr))
                            cluster_members.add(addr);
                    }
                    catch(Exception ex) {
                        log.warn("failed translating host %s into InetAddress: %s", host, ex);
                    }
                }
            }
        }

        if(use_disk_cache) {
            // this only makes sense if we have PDC below us
            Collection<PhysicalAddress> list=(Collection<PhysicalAddress>)down_prot.down(new Event(Event.GET_PHYSICAL_ADDRESSES));
            if(list != null)
                list.stream().filter(phys_addr -> !cluster_members.contains(phys_addr)).forEach(cluster_members::add);
        }

        if(log.isTraceEnabled())
            log.trace("%s: sending discovery requests to %s", local_addr, cluster_members);
        PingHeader hdr=new PingHeader(PingHeader.GET_MBRS_REQ).clusterName(cluster_name).initialDiscovery(initial_discovery);
        for(final PhysicalAddress addr: cluster_members) {
            if(physical_addr != null && addr.equals(physical_addr)) // no need to send the request to myself
                continue;

            // the message needs to be DONT_BUNDLE, see explanation above
            final Message msg=new Message(addr).setFlag(Message.Flag.INTERNAL, Message.Flag.DONT_BUNDLE, Message.Flag.OOB)
              .putHeader(this.id,hdr);
            if(data != null)
                msg.setBuffer(marshal(data));

            if(async_discovery_use_separate_thread_per_request)
                timer.execute(() -> sendDiscoveryRequest(msg), sends_can_block);
            else
                sendDiscoveryRequest(msg);
        }

    }

    @ManagedOperation(description="Asks Kubernetes for the IP addresses of all pods")
    public String fetchFromKube() {
        List<String> list=readAll();
        return list.stream().collect(Collectors.joining(", "));
    }


    protected List<String> readAll() {
        if(isClusteringEnabled() && client != null) {
            try {
                return client.getPods(namespace, labels, dump_requests);
            }
            catch(Exception e) {
                log.warn("failed getting JSON response from Kubernetes %s for cluster [%s], namespace [%s], labels [%s]; encountered [%s: %s]",
                         client.info(), cluster_name, namespace, labels, e.getClass().getName(), e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    protected void sendDiscoveryRequest(Message req) {
        try {
            down_prot.down(req);
        }
        catch(Throwable t) {
            log.trace("sending discovery request to %s failed: %s", req.dest(), t);
        }
    }

    @Override
    public String toString() {
        return String.format("KubePing{namespace='%s', labels='%s'}", namespace, labels);
    }


}
