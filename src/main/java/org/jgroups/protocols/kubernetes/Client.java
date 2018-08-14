package org.jgroups.protocols.kubernetes;

import mjson.Json;
import org.jgroups.logging.Log;
import org.jgroups.protocols.kubernetes.stream.StreamProvider;
import org.jgroups.util.Util;

import java.io.InputStream;
import java.util.*;

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
        if(dump_requests)
            System.out.printf("--> %s\n", url);
        try(InputStream stream = openStream(url, headers, connectTimeout, readTimeout, operationAttempts, operationSleep, streamProvider)) {
            return Util.readContents(stream);
        }
    }



    public List<Pod> getPods(String namespace, String labels, boolean dump_requests) throws Exception {
        String result=fetchFromKubernetes("pods", namespace, labels, dump_requests);
        if(result == null)
            return Collections.emptyList();
        return parseJsonResult(result, namespace, labels);
    }

    protected List<Pod> parseJsonResult(String input, String namespace, String labels) {
        if(input == null)
            return Collections.emptyList();
        Json json=Json.read(input);

        if(json == null || !json.isObject()) {
            log.error("JSON is not a map: %s", json);
            return Collections.emptyList();
        }

        if(!json.has("items")) {
            log.error("JSON object is missing property \"items\": %s", json);
            return Collections.emptyList();
        }
        List<Json> items=json.at("items").asJsonList();
        List<Pod> pods=new ArrayList<>();
        for(Json obj: items) {
            String parentDeployment = Optional.ofNullable(obj.at("metadata"))
                  .map(podMetadata -> podMetadata.at("labels"))
                  .map(podLabels -> podLabels.at("deployment"))
                  .map(Json::asString)
                  .orElse(null);
            String name = Optional.ofNullable(obj.at("metadata"))
                  .map(podMetadata -> podMetadata.at("name"))
                  .map(Json::asString)
                  .orElse(null);
            Json podStatus = Optional.ofNullable(obj.at("status")).orElse(null);
            String podIP = null;
            if(podStatus != null) {
                podIP = Optional.ofNullable(podStatus.at("podIP"))
                  .map(Json::asString)
                  .orElse(null);
            }
            boolean running = podRunning(podStatus);
            if(podIP == null || !running) {
                log.trace("Skipping pod %s since it's IP is %s or running is %s", name, podIP, Boolean.toString(running));
            } else {
                pods.add(new Pod(name, podIP, parentDeployment));
            }
        }
        log.trace("getPods(%s, %s) = %s", namespace, labels, pods);
        return pods;
    }
    
    /**
     * Helper method to determine if a pod is considered running or not.
     * 
     * @param podStatus a Json object expected to contain the "status" object of a pod
     * @return true if the pod is considered available, false otherwise
     */
    protected boolean podRunning(Json podStatus) {
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
        String phase = Optional.ofNullable(podStatus.at("phase"))
                .map(Json::asString)
                .orElse("not running");
        log.trace("  status.phase=%s", phase);
        if(!phase.equalsIgnoreCase("Running")) {
            return false;
        }
        // 2. and 3. status.message and status.reason
        String statusMessage = Optional.ofNullable(podStatus.at("message"))
                .map(Json::asString)
                .orElse(null);
        String statusReason = Optional.ofNullable(podStatus.at("reason"))
                .map(Json::asString)
                .orElse(null);
        log.trace("  status.message=%s and status.reason=%s", statusMessage, statusReason);
        if(statusMessage != null || statusReason != null) {
            return false;
        }
        // 4. status.containerStatuses.ready
        List<Json> containerStatuses = Optional.ofNullable(podStatus.at("containerStatuses"))
                .map(Json::asJsonList)
                .orElse(Collections.emptyList());
        boolean ready = true;
        // if we have no containerStatuses, we don't check for it and consider this condition as passed
        for(Json containerStatus: containerStatuses) {
            ready = ready && containerStatus.at("ready").asBoolean();
        }
        log.trace("  containerStatuses[].status of all container is %s", Boolean.toString(ready));
        if(!ready) {
            return false;
        }
        // 5. ready condition must be "True"
        Boolean readyCondition = Boolean.FALSE;
        List<Json> conditions = podStatus.at("conditions").asJsonList();
        // walk through all the conditions and find type=="Ready" and get the value of the status property
        for(Json condition: conditions) {
            String type = condition.at("type").asString();
            if(type.equalsIgnoreCase("Ready")) {
                readyCondition = new Boolean(condition.at("status").asString());
            }
        }
        log.trace(  "conditions with type==\"Ready\" has status property value = %s", readyCondition.toString());
        if(!readyCondition.booleanValue()) {
            return false;
        }
        return true;
    }
}
