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

package org.jgroups.ping.kube;

import static org.jgroups.ping.common.Utils.openStream;
import static org.jgroups.ping.common.Utils.urlencode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.dmr.ModelNode;
import org.jgroups.ping.common.stream.StreamProvider;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Client {
    private static final Logger log = Logger.getLogger(Client.class.getName());

    private final String masterUrl;
    private final Map<String, String> headers;
    private final int connectTimeout;
    private final int readTimeout;
    private final int operationAttempts;
    private final long operationSleep;
    private final StreamProvider streamProvider;
    private final String info;

    public Client(String masterUrl, Map<String, String> headers, int connectTimeout, int readTimeout, int operationAttempts, long operationSleep, StreamProvider streamProvider) {
        this.masterUrl = masterUrl;
        this.headers = headers;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.operationAttempts = operationAttempts;
        this.operationSleep = operationSleep;
        this.streamProvider = streamProvider;
        Map<String, String> maskedHeaders = new TreeMap<String, String>();
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                String key = header.getKey();
                String value = header.getValue();
                if ("Authorization".equalsIgnoreCase(key) && value != null) {
                    value = "#MASKED:" + value.length() + "#";
                }
                maskedHeaders.put(key, value);
            }
        }
        this.info = String.format("%s[masterUrl=%s, headers=%s, connectTimeout=%s, readTimeout=%s, operationAttempts=%s, operationSleep=%s, streamProvider=%s]",
                getClass().getSimpleName(), masterUrl, maskedHeaders, connectTimeout, readTimeout, operationAttempts, operationSleep, streamProvider);
    }

    public final String info() {
        return info;
    }

    protected ModelNode getNode(String op, String namespace, String labels) throws Exception {
        String url = masterUrl;
        if (namespace != null && namespace.length() > 0) {
            url = url + "/namespaces/" + urlencode(namespace);
        }
        url = url + "/" + op;
        if (labels != null && labels.length() > 0) {
            url = url + "?labelSelector=" + urlencode(labels);
        }
        try (InputStream stream = openStream(url, headers, connectTimeout, readTimeout, operationAttempts, operationSleep, streamProvider)) {
            return ModelNode.fromJSONStream(stream);
        }
    }

    public final List<Pod> getPods(String namespace, String labels) throws Exception {
        ModelNode root = getNode("pods", namespace, labels);
        List<Pod> pods = new ArrayList<Pod>();
        List<ModelNode> itemNodes = root.get("items").asList();
        for (ModelNode itemNode : itemNodes) {
            //ModelNode metadataNode = itemNode.get("metadata");
            //String podName = metadataNode.get("name").asString(); // eap-app-1-43wra
            //String podNamespace = metadataNode.get("namespace").asString(); // dward
            ModelNode specNode = itemNode.get("spec");
            //String serviceAccount = specNode.get("serviceAccount").asString(); // default
            //String host = specNode.get("host").asString(); // ce-openshift-rhel-minion-1.lab.eng.brq.redhat.com
            ModelNode statusNode = itemNode.get("status");
            ModelNode phaseNode = statusNode.get("phase");
            if (!phaseNode.isDefined() || !"Running".equals(phaseNode.asString())) {
                continue;
            }
            /* We don't want to filter on the following as that could result in MERGEs instead of JOINs.
            ModelNode conditionsNode = statusNode.get("conditions");
            if (!conditionsNode.isDefined()) {
                continue;
            }
            boolean ready = false;
            List<ModelNode> conditions = conditionsNode.asList();
            for (ModelNode condition : conditions) {
                ModelNode conditionTypeNode = condition.get("type");
                ModelNode conditionStatusNode = condition.get("status");
                if (conditionTypeNode.isDefined() && "Ready".equals(conditionTypeNode.asString()) &&
                        conditionStatusNode.isDefined() && "True".equals(conditionStatusNode.asString())) {
                    ready = true;
                    break;
                }
            }
            if (!ready) {
                continue;
            }
            */
            //String hostIP = statusNode.get("hostIP").asString(); // 10.34.75.250
            ModelNode podIPNode = statusNode.get("podIP");
            if (!podIPNode.isDefined()) {
                continue;
            }
            String podIP = podIPNode.asString(); // 10.1.0.169
            Pod pod = new Pod(podIP);
            ModelNode containersNode = specNode.get("containers");
            if (!containersNode.isDefined()) {
                continue;
            }
            List<ModelNode> containerNodes = containersNode.asList();
            for (ModelNode containerNode : containerNodes) {
                ModelNode portsNode = containerNode.get("ports");
                if (!portsNode.isDefined()) {
                    continue;
                }
                //String containerName = containerNode.get("name").asString(); // eap-app
                Container container = new Container();
                List<ModelNode> portNodes = portsNode.asList();
                for (ModelNode portNode : portNodes) {
                    ModelNode portNameNode = portNode.get("name");
                    if (!portNameNode.isDefined()) {
                        continue;
                    }
                    String portName = portNameNode.asString(); // ping
                    ModelNode containerPortNode = portNode.get("containerPort");
                    if (!containerPortNode.isDefined()) {
                        continue;
                    }
                    int containerPort = containerPortNode.asInt(); // 8888
                    Port port = new Port(portName, containerPort);
                    container.addPort(port);
                }
                pod.addContainer(container);
            }
            pods.add(pod);
        }
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, String.format("getPods(%s, %s) = %s", namespace, labels, pods));
        }
        return pods;
    }

    public boolean accept(Context context) {
        Container container = context.getContainer();
        List<Port> ports = container.getPorts();
        if (ports != null) {
            String pingPortName = context.getPingPortName();
            for (Port port : ports) {
                if (pingPortName.equalsIgnoreCase(port.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

}
