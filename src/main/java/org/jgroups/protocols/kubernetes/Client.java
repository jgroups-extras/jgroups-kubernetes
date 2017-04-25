package org.jgroups.protocols.kubernetes;

import org.jboss.dmr.ModelNode;
import org.jgroups.logging.Log;
import org.jgroups.protocols.kubernetes.stream.StreamProvider;

import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.jgroups.protocols.kubernetes.Utils.openStream;
import static org.jgroups.protocols.kubernetes.Utils.urlencode;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Client {
    protected final String              masterUrl;
    protected final Map<String, String> headers;
    protected final int                 connectTimeout;
    protected final int                 readTimeout;
    protected final int                 operationAttempts;
    protected final long                operationSleep;
    protected final StreamProvider      streamProvider;
    protected final String              info;
    protected final Log                 log;

    public Client(String masterUrl, Map<String, String> headers, int connectTimeout, int readTimeout, int operationAttempts,
                  long operationSleep, StreamProvider streamProvider, Log log) {
        this.masterUrl = masterUrl;
        this.headers = headers;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.operationAttempts = operationAttempts;
        this.operationSleep = operationSleep;
        this.streamProvider = streamProvider;
        this.log=log;
        Map<String, String> maskedHeaders=new TreeMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                String key = header.getKey();
                String value = header.getValue();
                if ("Authorization".equalsIgnoreCase(key) && value != null)
                    value = "#MASKED:" + value.length() + "#";
                maskedHeaders.put(key, value);
            }
        }
        info=String.format("%s[masterUrl=%s, headers=%s, connectTimeout=%s, readTimeout=%s, operationAttempts=%s, " +
                             "operationSleep=%s, streamProvider=%s]",
                           getClass().getSimpleName(), masterUrl, maskedHeaders, connectTimeout, readTimeout,
                           operationAttempts, operationSleep, streamProvider);
    }

    public final String info() {
        return info;
    }

    protected ModelNode getNode(String op, String namespace, String labels, boolean dump_requests) throws Exception {
        String url = masterUrl;
        if(namespace != null && !namespace.isEmpty())
            url = url + "/namespaces/" + urlencode(namespace);
        url = url + "/" + op;
        if(labels != null && !labels.isEmpty())
            url = url + "?labelSelector=" + urlencode(labels);
        if(dump_requests)
            System.out.printf("--> %s\n", url);
        try(InputStream stream = openStream(url, headers, connectTimeout, readTimeout, operationAttempts, operationSleep, streamProvider)) {
            return ModelNode.fromJSONStream(stream);
        }
    }


    public final List<InetAddress> getPods(String namespace, String labels, boolean dump_requests) throws Exception {
        ModelNode root = getNode("pods", namespace, labels, dump_requests);
        List<InetAddress> pods =new ArrayList<>();
        List<ModelNode> itemNodes = root.get("items").asList();
        for (ModelNode itemNode : itemNodes) {
            //ModelNode metadataNode = itemNode.get("metadata");
            //String podName = metadataNode.get("name").asString(); // eap-app-1-43wra
            //String podNamespace = metadataNode.get("namespace").asString(); // dward
            // ModelNode specNode = itemNode.get("spec");
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
            if (!podIPNode.isDefined())
                continue;
            String podIP = podIPNode.asString(); // 10.1.0.169
            try {
                pods.add(InetAddress.getByName(podIP));
            }
            catch(Exception ex) {
                log.error("failed converting podIP (%s) to InetAddress: %s", podIP, ex);
            }
        }
        log.trace("getPods(%s, %s) = %s", namespace, labels, pods);
        return pods;
    }

}
