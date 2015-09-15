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
package org.openshift.activemq.discoveryagent.kube;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.jboss.dmr.ModelNode;
import org.openshift.activemq.discoveryagent.PeerAddressResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KubePeerAddressResolver
 */
public class KubePeerAddressResolver implements PeerAddressResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubePeerAddressResolver.class);

    private static final String ENV_AMQ_MESH_SERVICE_NAMESPACE = "AMQ_MESH_SERVICE_NAMESPACE";
    private static final String ENV_KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
    private static final String ENV_KUBERNETES_SERVICE_PORT = "KUBERNETES_SERVICE_PORT";

    private static final String SERVICE_ACCOUNT_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    private static final String DEFAULT_KUBERNETES_SERVICE_HOST = "kubernetes.default.svc";
    private static final int DEFAULT_KUBERNETES_SERVICE_PORT = 443;
    private static final String DEFAULT_KUBERNETES_VERSION = "v1";
    private static final String DEFAULT_KUBERNETES_PROTOCOL = "https";

    private static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    private static final int DEFAULT_READ_TIMEOUT = 30000;
    private static final int DEFAULT_OPERATION_ATTEMPTS = 3;
    private static final int DEFAULT_OPERATION_SLEEP = 1000;

    private final InsecureStreamProvider streamProvider;
    private final String url;
    private final Map<String, String> headers;
    private final int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private final int readTimeout = DEFAULT_READ_TIMEOUT;
    private final int operationAttempts = DEFAULT_OPERATION_ATTEMPTS;
    private final long operationSleep = DEFAULT_OPERATION_SLEEP;

    private final String serviceNamespace;
    private final String serviceName;

    private int servicePort = -1;
    private boolean portChecked;

    /**
     * Create a new KubePeerAddressResolver.
     * 
     * @param serviceName the service name
     * @param servicePort the service port
     * 
     * @throws Exception if something goes awry
     */
    public KubePeerAddressResolver(String serviceName, int servicePort) throws Exception {
        this.serviceName = serviceName;
        this.servicePort = servicePort;

        this.serviceNamespace = getSystemEnv(ENV_AMQ_MESH_SERVICE_NAMESPACE, null, true);

        final String masterHost = getSystemEnv(ENV_KUBERNETES_SERVICE_HOST, DEFAULT_KUBERNETES_SERVICE_HOST, true);
        final int masterPort = getSystemEnvInt(ENV_KUBERNETES_SERVICE_PORT, DEFAULT_KUBERNETES_SERVICE_PORT);
        final String masterApiVersion = DEFAULT_KUBERNETES_VERSION;
        final String masterProtocol = DEFAULT_KUBERNETES_PROTOCOL;
        final String saTokenFile = SERVICE_ACCOUNT_TOKEN_PATH;

        this.url = String.format("%s://%s:%s/api/%s/namespaces/%s/endpoints/%s", masterProtocol, masterHost,
                masterPort, masterApiVersion, urlencode(serviceNamespace), urlencode(serviceName));

        final String saToken = readFileToString(new File(saTokenFile));
        if (saToken == null) {
            this.headers = Collections.emptyMap();
        } else {
            this.headers = Collections.singletonMap("Authorization", "Bearer " + saToken);
        }

        this.streamProvider = new InsecureStreamProvider();
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public synchronized String[] getPeerIPs() {
        List<String> ips = new ArrayList<String>();
        try {
            ModelNode rootNode = getEndpointsNode();
            if (rootNode.hasDefined("subsets")) {
                List<ModelNode> subsetsNode = rootNode.get("subsets").asList();
                for (ModelNode subsetNode : subsetsNode) {
                    if (subsetNode.hasDefined("addresses")) {
                        List<ModelNode> addressesNode = subsetNode.get("addresses").asList();
                        for (ModelNode addressNode : addressesNode) {
                            String ip = addressNode.get("ip").asString();
                            if (ip != null && !ip.isEmpty()) {
                                ips.add(ip);
                            }
                        }
                    }
                    if (subsetNode.hasDefined("ports")) {
                        initServicePort(subsetNode.get("ports").asList());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error retrieving service endpoints from Kubernetes", e);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("getEndpoints(%s, %s) = %s", serviceNamespace, serviceName, ips));
        }
        return ips.toArray(new String[ips.size()]);
    }

    @Override
    public synchronized int getServicePort() {
        if (!portChecked) {
            getPeerIPs();
        }
        return servicePort;
    }

    private void initServicePort(List<ModelNode> portsNode) {
        if (!portChecked) {
            for (ModelNode portNode : portsNode) {
                int port = portNode.get("port").asInt();
                if (servicePort <= 0) {
                    servicePort = port;
                } else if (servicePort != port) {
                    LOGGER.warn(String
                            .format("Service port mismatch: using (%d), but detected (%d)", servicePort, port));
                }
                portChecked = true;
                break;
            }
        }
    }

    private ModelNode getEndpointsNode() throws Exception {
        try (InputStream stream = openStream(url, headers, connectTimeout, readTimeout, operationAttempts,
                operationSleep, streamProvider)) {
            return ModelNode.fromJSONStream(stream);
        }
    }

    private String urlencode(String s) {
        try {
            return s != null ? URLEncoder.encode(s, "UTF-8") : null;
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(
                    String.format("Could not encode String [%s] as UTF-8 (which should always be supported)."), uee);
        }
    }

    private InputStream openStream(final String url, final Map<String, String> headers, final int connectTimeout,
            final int readTimeout, final int attempts, final long sleep, final InsecureStreamProvider streamProvider)
            throws Exception {
        return execute(new Callable<InputStream>() {
            public InputStream call() throws Exception {
                return streamProvider.openStream(url, headers, connectTimeout, readTimeout);
            }
        }, attempts, sleep, true);
    }

    private <V> V execute(Callable<V> callable, int attempts, long sleep, boolean throwOnFail) throws Exception {
        V value = null;
        int tries = attempts;
        Throwable lastFail = null;
        while (tries > 0) {
            tries--;
            try {
                value = callable.call();
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
        if (lastFail != null && (throwOnFail || LOGGER.isInfoEnabled())) {
            String emsg = String.format(
                    "%s attempt(s) with a %sms sleep to execute [%s] failed. Last failure was [%s: %s]", attempts,
                    sleep, callable.getClass().getSimpleName(), lastFail.getClass().getName(), lastFail.getMessage());
            if (throwOnFail) {
                throw new Exception(emsg, lastFail);
            } else {
                LOGGER.info(emsg);
            }
        }
        return value;
    }

    private String getSystemEnv(final String key, final String def, final boolean trimToNull) {
        if (key != null) {
            String val = AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    return System.getenv(key);
                }
            });
            if (trimToNull) {
                val = trimToNull(val);
            }
            if (val != null) {
                return val;
            }
        }
        return def;
    }

    private int getSystemEnvInt(final String key, final int def) {
        String val = getSystemEnv(key, null, true);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException nfe) {
                LOGGER.warn(String.format("system environment variable [%s] with value [%s] is not an integer: %s",
                        key, val, nfe.getMessage()));
            }
        }
        return def;
    }

    private String readFileToString(File file) throws IOException {
        if (file != null && file.canRead()) {
            Path path = FileSystems.getDefault().getPath(file.getCanonicalPath());
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, "UTF-8");
        }
        return null;
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
