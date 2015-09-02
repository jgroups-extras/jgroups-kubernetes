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

package org.openshift.ping.kube;

import static org.openshift.ping.common.Utils.getSystemEnv;
import static org.openshift.ping.common.Utils.getSystemEnvInt;
import static org.openshift.ping.common.Utils.readFileToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jgroups.annotations.MBean;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.PingData;
import org.openshift.ping.common.OpenshiftPing;
import org.openshift.ping.common.stream.CertificateStreamProvider;
import org.openshift.ping.common.stream.InsecureStreamProvider;
import org.openshift.ping.common.stream.StreamProvider;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@MBean(description = "Kubernetes based discovery protocol")
public class KubePing extends OpenshiftPing {

    public static final short OPENSHIFT_KUBE_PING_ID = 2010;
    public static final short JGROUPS_KUBE_PING_ID = 2011;
    static {
        ClassConfigurator.addProtocol(OPENSHIFT_KUBE_PING_ID, KubePing.class);
    }

    @Property
    private String masterProtocol;

    @Property
    private String masterHost;

    @Property
    private int masterPort;

    @Property
    private String apiVersion = "v1";

    @Property
    private String namespace; // DO NOT HARDCODE A DEFAULT (i.e.: "default") - SEE isClusteringEnabled() and init() METHODS BELOW!
    private String _namespace;

    @Property
    private String labels;
    private String _labels;

    @Property
    private int serverPort = 8888;
    private int _serverPort;

    @Property
    private String pingPortName = "ping";
    private String _pingPortName;

    @Property
    private String clientCertFile;

    @Property
    private String clientKeyFile;

    @Property
    private String clientKeyPassword;

    @Property
    private String clientKeyAlgo = "RSA";

    @Property
    private String caCertFile;

    @Property
    private String saTokenFile = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    private Client _client;

    public KubePing() {
        super("OPENSHIFT_KUBE_PING_");
    }

    public void setMasterProtocol(String masterProtocol) {
        this.masterProtocol = masterProtocol;
    }

    public void setMasterHost(String masterMost) {
        this.masterHost = masterMost;
    }

    public void setMasterPort(int masterPort) {
        this.masterPort = masterPort;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    protected boolean isClusteringEnabled() {
        return _namespace != null;
    }

    @Override
    protected int getServerPort() {
        return _serverPort;
    }

    protected Client getClient() {
        return _client;
    }

    public void init() throws Exception {
        super.init();
        _namespace = getSystemEnv(getSystemEnvName("NAMESPACE"), namespace, true);
        if (_namespace == null) {
            if (log.isInfoEnabled()) {
                log.info(String.format("namespace not set; clustering disabled"));
            }
            // no further initialization necessary
            return;
        }
        if (log.isInfoEnabled()) {
            log.info(String.format("namespace [%s] set; clustering enabled", _namespace));
        }
        String mProtocol = getSystemEnv(getSystemEnvName("MASTER_PROTOCOL"), masterProtocol, true);
        String mHost;
        int mPort;
        Map<String, String> headers = new HashMap<String, String>();
        StreamProvider streamProvider;
        String cCertFile = getSystemEnv(new String[]{getSystemEnvName("CLIENT_CERT_FILE"), "KUBERNETES_CLIENT_CERTIFICATE_FILE"}, clientCertFile, true);
        if (cCertFile != null) {
            if (mProtocol == null) {
                mProtocol = "http";
            }
            mHost = getSystemEnv(new String[]{getSystemEnvName("MASTER_HOST"), "KUBERNETES_RO_SERVICE_HOST"}, masterHost, true);
            mPort = getSystemEnvInt(new String[]{getSystemEnvName("MASTER_PORT"), "KUBERNETES_RO_SERVICE_PORT"}, masterPort);
            String cKeyFile = getSystemEnv(new String[]{getSystemEnvName("CLIENT_KEY_FILE"), "KUBERNETES_CLIENT_KEY_FILE"}, clientKeyFile, true);
            String cKeyPassword = getSystemEnv(new String[]{getSystemEnvName("CLIENT_KEY_PASSWORD"), "KUBERNETES_CLIENT_KEY_PASSWORD"}, clientKeyPassword, false);
            String cKeyAlgo = getSystemEnv(new String[]{getSystemEnvName("CLIENT_KEY_ALGO"), "KUBERNETES_CLIENT_KEY_ALGO"}, clientKeyAlgo, true);
            String lCaCertFile = getSystemEnv(new String[]{getSystemEnvName("CA_CERT_FILE"), "KUBERNETES_CA_CERTIFICATE_FILE"}, caCertFile, true);
            streamProvider = new CertificateStreamProvider(cCertFile, cKeyFile, cKeyPassword, cKeyAlgo, lCaCertFile);
        } else {
            if (mProtocol == null) {
                mProtocol = "https";
            }
            mHost = getSystemEnv(new String[]{getSystemEnvName("MASTER_HOST"), "KUBERNETES_SERVICE_HOST"}, masterHost, true);
            mPort = getSystemEnvInt(new String[]{getSystemEnvName("MASTER_PORT"), "KUBERNETES_SERVICE_PORT"}, masterPort);
            String saToken = readFileToString(getSystemEnv(getSystemEnvName("SA_TOKEN_FILE"), saTokenFile, true));
            if (saToken != null) {
                // curl -k -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
                // https://172.30.0.2:443/api/v1/namespaces/dward/pods?labels=application%3Deap-app
                headers.put("Authorization", "Bearer " + saToken);
            }
            streamProvider = new InsecureStreamProvider();
        }
        String ver = getSystemEnv(getSystemEnvName("API_VERSION"), apiVersion, true);
        String url = String.format("%s://%s:%s/api/%s", mProtocol, mHost, mPort, ver);
        _labels = getSystemEnv(getSystemEnvName("LABELS"), labels, true);
        _pingPortName = getSystemEnv(getSystemEnvName("PORT_NAME"), pingPortName, true);
        _serverPort = getSystemEnvInt(getSystemEnvName("SERVER_PORT"), serverPort);
        _client = new Client(url, headers, getConnectTimeout(), getReadTimeout(), getOperationAttempts(), getOperationSleep(), streamProvider);
    }

    @Override
    public void destroy() {
        _namespace = null;
        _labels = null;
        _serverPort = 0;
        _pingPortName = null;
        _client = null;
        super.destroy();
    }

    @Override
    protected synchronized List<PingData> doReadAll(String clusterName) {
        Client client = getClient();
        List<Pod> pods;
        try {
            pods = client.getPods(_namespace, _labels);
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn(String.format("Problem getting Pod json from Kubernetes %s for cluster [%s], namespace [%s], labels [%s]; encountered [%s: %s]",
                        client.info(), clusterName, _namespace, _labels, e.getClass().getName(), e.getMessage()));
            }
            pods = Collections.<Pod>emptyList();
        }
        List<PingData> retval = new ArrayList<>();
        boolean localAddrPresent = false;
        for (Pod pod : pods) {
            List<Container> containers = pod.getContainers();
            for (Container container : containers) {
                Context context = new Context(container, _pingPortName);
                if (client.accept(context)) {
                    String podIP = pod.getPodIP();
                    int containerPort = container.getPort(_pingPortName).getContainerPort();
                    try {
                        PingData pingData = getPingData(podIP, containerPort, clusterName);
                        localAddrPresent = localAddrPresent || pingData.getAddress().equals(local_addr);
                        retval.add(pingData);
                    } catch (Exception e) {
                        if (log.isInfoEnabled()) {
                            log.info(String.format("PingData not available for cluster [%s], podIP [%s], containerPort [%s]; encountered [%s: %s]",
                                    clusterName, podIP, containerPort, e.getClass().getName(), e.getMessage()));
                        }
                    }
                }
            }
        }
        if (localAddrPresent) {
            if (log.isDebugEnabled()) {
                for (PingData pingData: retval) {
                    log.debug(String.format("Returning PingData [%s]", pingData));
                }
            }
            return retval;
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Local address not discovered, returning empty list");
            }
            return Collections.<PingData>emptyList();
        }
    }

}
