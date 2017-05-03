
package org.jgroups.protocols.kubernetes;

import org.jgroups.Address;
import org.jgroups.PhysicalAddress;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.TCPPING;
import org.jgroups.protocols.TP;
import org.jgroups.protocols.kubernetes.stream.CertificateStreamProvider;
import org.jgroups.protocols.kubernetes.stream.InsecureStreamProvider;
import org.jgroups.protocols.kubernetes.stream.StreamProvider;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Responses;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class KUBE_PING extends TCPPING {
    protected static final short KUBERNETES_PING_ID=2017;


    static {
        ClassConfigurator.addProtocol(KUBERNETES_PING_ID, KUBE_PING.class);
    }


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

    @Property(description="http (default) or https. Used to send the initial discovery request to the Kubernetes server",
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
        populateInitialHosts();
    }


    @Override public void destroy() {
        client=null;
        super.destroy();
    }

    public void findMembers(List<Address> members, boolean initial_discovery, Responses responses) {
        if(!initial_discovery)
            populateInitialHosts();
        super.findMembers(members, initial_discovery, responses);
    }

    @ManagedOperation(description="Asks Kubernetes for the IP addresses of all pods")
    public String fetchFromKube() {
        List<InetAddress> list=readAll();
        return list.stream().map(InetAddress::getHostAddress).collect(Collectors.joining(", "));
    }

    protected void populateInitialHosts() {
        List<InetAddress> hosts=readAll();
        if(dump_requests)
            System.out.printf("<-- %s\n", hosts);
        if(hosts == null || hosts.isEmpty()) {
            log.warn("initial_hosts could not be populated with information from Kubernetes");
            return;
        }
        List<PhysicalAddress> tcpping_hosts=getInitialHosts();
        tcpping_hosts.clear(); // remove left members when scaling down
        // clearDynamicHostList(); // ?

        for(InetAddress host: hosts) {
            for(int i=0; i <= getPortRange(); i++) {
                IpAddress addr=new IpAddress(host, tp_bind_port+i);
                if(!tcpping_hosts.contains(addr)) {
                    tcpping_hosts.add(addr);
                    log.debug("added %s to initial_hosts", addr);
                }
            }
        }
    }


    protected List<InetAddress> readAll() {
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

    @Override
    public String toString() {
        return String.format("KubePing{namespace='%s', labels='%s'}", namespace, labels);
    }


}
