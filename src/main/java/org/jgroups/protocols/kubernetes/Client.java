package org.jgroups.protocols.kubernetes;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.jgroups.logging.Log;
import org.jgroups.protocols.kubernetes.stream.StreamProvider;
import org.jgroups.protocols.kubernetes.stream.TokenStreamProvider;
import org.jgroups.util.Util;

import java.io.InputStream;
import java.io.StringReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.jgroups.protocols.kubernetes.Utils.openStream;
import static org.jgroups.protocols.kubernetes.Utils.urlencode;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author Radoslav Husar
 */
public class Client {
    protected final String              masterUrl;
    protected final Map<String, String> headers;
    protected final int                 connectTimeout;
    protected final int                 readTimeout;
    protected final int                 operationAttempts;
    protected final long                operationSleep;
    protected final StreamProvider      streamProvider;
    protected final Class<? extends InetAddress> preferredAddressType;
    protected final String              info;
    protected final Log                 log;

    /**
     * @deprecated use {@link #Client(String, Map, int, int, int, long, StreamProvider, Class, Log)} instead
     */
    @Deprecated(since = "3.0.1", forRemoval = true)
    public Client(String masterUrl, Map<String, String> headers, int connectTimeout, int readTimeout, int operationAttempts,
                  long operationSleep, StreamProvider streamProvider, Log log) {
        this(masterUrl, headers, connectTimeout, readTimeout, operationAttempts, operationSleep, streamProvider, null, log);
    }

    /**
     * @param preferredAddressType the address family ({@link Inet4Address} or {@link Inet6Address}) of the pod IPs to
     *                             discover; if null, or if a pod has no IP of that family, the primary pod IP is used
     */
    public Client(String masterUrl, Map<String, String> headers, int connectTimeout, int readTimeout, int operationAttempts,
                  long operationSleep, StreamProvider streamProvider, Class<? extends InetAddress> preferredAddressType, Log log) {
        this.masterUrl = masterUrl;
        this.headers = headers;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.operationAttempts = operationAttempts;
        this.operationSleep = operationSleep;
        this.streamProvider = streamProvider;
        this.preferredAddressType = preferredAddressType;
        this.log=log;

        Map<String, String> maskedHeaders=new TreeMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                String key = header.getKey();
                String value = header.getValue();
                if (TokenStreamProvider.AUTHORIZATION.equalsIgnoreCase(key) && value != null)
                    value = "#MASKED:" + value.length() + "#";
                maskedHeaders.put(key, value);
            }
        }
        info=String.format("%s[masterUrl=%s, headers=%s, connectTimeout=%s, readTimeout=%s, operationAttempts=%s, " +
                             "operationSleep=%s, streamProvider=%s, preferredAddressType=%s]",
                           getClass().getSimpleName(), masterUrl, maskedHeaders, connectTimeout, readTimeout,
                           operationAttempts, operationSleep, streamProvider,
                           preferredAddressType != null ? preferredAddressType.getSimpleName() : null);
    }

    public String info() {
        return info;
    }

    protected String fetchFromKubernetes(String op, String namespace, String labels, boolean dump_requests) throws Exception {
        String url = masterUrl;
        if(namespace != null && !namespace.isEmpty())
            url = url + "/namespaces/" + urlencode(namespace);
        url = url + "/" + op;
        if(labels != null && !labels.isEmpty())
            url = url + "?labelSelector=" + urlencode(labels);

        InputStream stream=null;
        String retval;
        try {
            stream=openStream(url, headers, connectTimeout, readTimeout, operationAttempts, operationSleep, streamProvider);
            retval=Util.readContents(stream);
            if(dump_requests)
                System.out.printf("--> %s\n<-- %s\n", url, retval);
            return retval;
        }
        catch(Throwable t) {
            if(dump_requests)
                System.out.printf("--> %s\n<-- ERROR: %s\n", url, t.getMessage());
            throw t;
        }
        finally {
            Util.close(stream);
        }
    }

    public List<Pod> getPods(String namespace, String labels, boolean dump_requests) throws Exception {
        String result = fetchFromKubernetes("pods", namespace, labels, dump_requests);
        if(result == null)
            return Collections.emptyList();
        return parseJsonResult(result, namespace, labels);
    }

    /**
     * get pod group during Rolling Update
     * @param pod - JsonObject returned by k8s
     * @return pod group string, or null if not determinable
     */
    String getPodGroup(JsonObject pod) {
        JsonObject meta = pod.getJsonObject("metadata");
        if (meta == null) {
            log.debug("pod %s, group %s", null, null);
            return null;
        }
        JsonObject labels = meta.getJsonObject("labels");

        String group = null;
        if (labels != null) {
            // This works for Deployment Config
            group = labels.getString("pod-template-hash", null);
        }

        if (group == null && labels != null) {
            // Ok, maybe, it's a Deployment and has a valid deployment flag?
            group = labels.getString("deployment", null);
        }

        if (group == null && labels != null) {
            // Final check, maybe it's a StatefulSet?
            group = labels.getString("controller-revision-hash", null);
        }

        log.debug("pod %s, group %s", meta.getString("name", null), group);
        return group;
    }

    protected List<Pod> parseJsonResult(String input, String namespace, String labels) {
        if(input == null)
            return Collections.emptyList();

        JsonValue value;
        try (JsonReader reader = Json.createReader(new StringReader(input))) {
            value = reader.read();
        } catch (Exception e) {
            log.error("Failed to parse JSON: %s", e.getMessage());
            return Collections.emptyList();
        }

        if(!(value instanceof JsonObject)) {
            log.error("JSON is not a map: %s", value);
            return Collections.emptyList();
        }
        JsonObject json = value.asJsonObject();

        if(!json.containsKey("items")) {
            log.error("JSON object is missing property \"items\": %s", json);
            return Collections.emptyList();
        }

        JsonArray items = json.getJsonArray("items");
        List<Pod> pods=new ArrayList<>();
        for(JsonValue item: items) {
            JsonObject obj = item.asJsonObject();
            String parentDeployment = getPodGroup(obj);
            JsonObject metadata = obj.getJsonObject("metadata");
            String name = metadata != null ? metadata.getString("name", null) : null;
            JsonObject podStatus = obj.getJsonObject("status");
            String podIP = podStatus != null ? podStatus.getString("podIP", null) : null;
            // Dual-stack pods list an IP of each family in podIPs; prefer the one matching the transport unless the primary IP already does
            if(podStatus != null && preferredAddressType != null && (podIP == null || addressType(podIP) != preferredAddressType)) {
                JsonArray podIPs = podStatus.getJsonArray("podIPs");
                String preferredIP = null;
                if(podIPs != null) {
                    for(JsonValue entry : podIPs) {
                        String ip = entry.asJsonObject().getString("ip", null);
                        if(ip != null && addressType(ip) == preferredAddressType) {
                            preferredIP = ip;
                            break;
                        }
                    }
                }
                if(preferredIP != null)
                    podIP = preferredIP;
                else if(podIP != null)
                    log.debug("Pod %s has no %s IP, using primary pod IP %s", name, preferredAddressType.getSimpleName(), podIP);
            }
            boolean running = podRunning(podStatus);
            if(podIP == null) {
                log.trace("Skipping pod %s since its IP is %s", name, podIP);
            } else {
                pods.add(new Pod(name, podIP, parentDeployment, running));
            }
        }
        log.trace("getPods(%s, %s) = %s", namespace, labels, pods);
        return pods;
    }

    /**
     * Determines the address family of a pod IP without parsing it; Kubernetes reports pod IPs as IP addresses, never
     * as hostnames.
     *
     * @param ip an IPv4 or IPv6 address literal
     * @return {@link Inet6Address} if the literal is an IPv6 address, {@link Inet4Address} otherwise
     * @see <a href="https://kubernetes.io/docs/reference/kubernetes-api/core/pod-v1/#PodStatus">PodStatus podIP and podIPs</a>
     */
    private static Class<? extends InetAddress> addressType(String ip) {
        return ip.contains(":") ? Inet6Address.class : Inet4Address.class;
    }

    /**
     * Helper method to determine if a pod is considered running or not.
     *
     * @param podStatus a JsonObject expected to contain the "status" object of a pod
     * @return true if the pod is considered available, false otherwise
     */
    protected boolean podRunning(JsonObject podStatus) {
        if(podStatus == null) {
            return false;
        }

        /*
         * A pod can only be considered 'running' if the following conditions are all true:
         * 1. status.phase == "Running",
         * 2. status.message is Undefined (does not exist)
         * 3. status.reason is Undefined (does not exist)
         * 4. all of status.containerStatuses[*].ready == true
         * 5. for conditions[*].type == "Ready" conditions[*].status must be "True"
         */
        // walk through each condition step by step
        // 1 status.phase
        log.trace("Determining pod status");
        String phase = podStatus.getString("phase", "not running");
        log.trace("  status.phase=%s", phase);
        if(!phase.equalsIgnoreCase("Running")) {
            return false;
        }
        // 2. and 3. status.message and status.reason
        String statusMessage = podStatus.getString("message", null);
        String statusReason = podStatus.getString("reason", null);
        log.trace("  status.message=%s and status.reason=%s", statusMessage, statusReason);
        if(statusMessage != null || statusReason != null) {
            return false;
        }
        // 4. status.containerStatuses.ready
        JsonArray containerStatuses = podStatus.getJsonArray("containerStatuses");
        boolean ready = true;
        // if we have no containerStatuses, we don't check for it and consider this condition as passed
        if(containerStatuses != null) {
            for(JsonValue containerStatusValue: containerStatuses) {
                JsonObject containerStatus = containerStatusValue.asJsonObject();
                if(!containerStatus.getBoolean("ready", false)) {
                    ready = false;
                    break;
                }
            }
        }
        log.trace("  containerStatuses[].status of all container is %s", Boolean.toString(ready));
        if(!ready) {
            return false;
        }
        // 5. ready condition must be "True"
        boolean readyCondition = false;
        JsonArray conditions = podStatus.getJsonArray("conditions");
        if(conditions != null) {
            // walk through all the conditions and find type=="Ready" and get the value of the status property
            for(JsonValue conditionValue: conditions) {
                JsonObject condition = conditionValue.asJsonObject();
                String type = condition.getString("type", null);
                if("Ready".equalsIgnoreCase(type)) {
                    readyCondition = Boolean.parseBoolean(condition.getString("status", null));
                }
            }
        }
        log.trace("  conditions with type==\"Ready\" has status property value = %s", readyCondition);

        return readyCondition;
    }
}
