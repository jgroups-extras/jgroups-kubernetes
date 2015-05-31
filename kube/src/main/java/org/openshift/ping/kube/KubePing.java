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

import java.util.ArrayList;
import java.util.List;

import org.jgroups.Address;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.FILE_PING;
import org.jgroups.protocols.PingData;
import org.openshift.ping.server.Server;
import org.openshift.ping.server.ServerFactory;
import org.openshift.ping.server.Utils;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@MBean(description = "Kubernetes based discovery protocol")
public class KubePing extends FILE_PING {

    public static final short OPENSHIFT_KUBE_PING_ID = 2001;
    public static final short JGROUPS_KUBE_PING_ID = 2002;
    static {
        ClassConfigurator.addProtocol(OPENSHIFT_KUBE_PING_ID, KubePing.class);
    }

    @Property
    private String host;

    @Property
    private String port;

    @Property
    private String version;

    @Property
    private int serverPort;

    @Property
    private String labelsQuery;
    
    @Property
    private String namespace;

    @Property
    private String pingPortName = "ping";

    @Property
    private String certFile;

    @Property
    private String keyFile;

    @Property
    private String keyPassword;

    @Property
    private String keyAlgo;

    @Property
    private String caFile;

    private ServerFactory factory;
    private Server server;
    private Client client;

    public void setFactory(ServerFactory factory) {
        this.factory = factory;
    }

    private String getHost() {
        if (host != null) {
            return host;
        } else {
            String omh = trimToNull(System.getenv("OPENSHIFT_MASTER_HOST"));
            if (omh != null) {
                return omh;
            } else {
                return System.getenv("KUBERNETES_RO_SERVICE_HOST");
            }
        }
    }

    public void setHost(String host) {
        this.host = host;
    }

    private String getPort() {
        if (port != null) {
            return port;
        } else {
            String omp = trimToNull(System.getenv("OPENSHIFT_MASTER_PORT"));
            if (omp != null) {
                return omp;
            } else {
                return System.getenv("KUBERNETES_RO_SERVICE_PORT");
            }
        }
    }

    public void setPort(String port) {
        this.port = port;
    }

    private String getVersion() {
        if (version != null) {
            return version;
        } else {
            return "v1beta1";
        }
    }

    private int getServerPort() {
        if (serverPort > 0) {
            return serverPort;
        } else {
            return 8888;
        }
    }

    protected Certs createCerts() throws Exception {
        if (getCertFile() != null) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Using certificate: %s", getCertFile()));
            }
            return new Certs(getCertFile(), getKeyFile(), getKeyPassword(), getKeyAlgo(), getCaFile());
        } else {
            if (log.isDebugEnabled()) {
                log.debug("No certificate configured.");
            }
            return null;
        }
    }

    protected Client createClient() throws Exception {
        return new Client(getHost(), getPort(), getVersion(), createCerts());
    }

    @Override
    public void start() throws Exception {
        client = createClient();
        if (log.isDebugEnabled()) {
            log.debug(client.info());
        }
        if (factory != null) {
            server = factory.getServer(getServerPort());
        } else {
            server = Utils.getServer(getServerPort());
        }
        final String serverName = server.getClass().getSimpleName();
        if (log.isInfoEnabled()) {
            log.info(String.format("Starting %s on port %s for channel address: %s", serverName, getServerPort(), stack.getChannel().getAddress()));
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
                log.info(String.format("Stopping %s", serverName));
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
        try {
            List<Pod> pods = client.getPods(getNamespace(), getLabelsQuery());
            for (Pod pod : pods) {
                List<Container> containers = pod.getContainers();
                for (Container container : containers) {
                    Context context = new Context(container, getPingPortName());
                    if (client.accept(context)) {
                        int port = container.getPort(getPingPortName()).getContainerPort();
                        retval.add(client.getPingData(container.getPodIP(), port, clusterName));
                    }
                }
            }
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn(String.format("Failed to read ping data from Kubernetes [%s] for cluster [%s]", client.info(), clusterName), e);
            }
        }
        return retval;
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

    public String getLabelsQuery() {
        return labelsQuery;
    }

    public void setLabelsQuery(String labelsQuery) {
        this.labelsQuery = labelsQuery;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPingPortName() {
        return pingPortName;
    }

    public void setPingPortName(String pingPortName) {
        this.pingPortName = pingPortName;
    }

    public String getCertFile() {
        if (certFile != null) {
            return certFile;
        } else {
            return System.getenv("KUBERNETES_CLIENT_CERTIFICATE_FILE");
        }
    }

    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    public String getKeyFile() {
        if (keyFile != null) {
            return keyFile;
        } else {
            return System.getenv("KUBERNETES_CLIENT_KEY_FILE");
        }
    }

    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    public String getKeyPassword() {
        if (keyPassword != null) {
            return keyPassword;
        } else {
            return System.getenv("KUBERNETES_CLIENT_KEY_PASSWORD");
        }
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getKeyAlgo() {
        if (keyAlgo != null) {
            return keyAlgo;
        } else {
            return System.getenv("KUBERNETES_CLIENT_KEY_ALGO");
        }
    }

    public void setKeyAlgo(String keyAlgo) {
        this.keyAlgo = keyAlgo;
    }

    public String getCaFile() {
        if (caFile != null) {
            return caFile;
        } else {
            return System.getenv("KUBERNETES_CA_CERTIFICATE_FILE");
        }
    }

    public void setCaFile(String caFile) {
        this.caFile = caFile;
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
