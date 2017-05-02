package org.jgroups.protocols.kubernetes;

import mjson.Json;
import org.jgroups.logging.Log;
import org.jgroups.protocols.kubernetes.stream.StreamProvider;
import org.jgroups.util.Util;

import java.io.InputStream;
import java.net.InetAddress;
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




    public List<InetAddress> getPods(String namespace, String labels, boolean dump_requests) throws Exception {
        String result=fetchFromKubernetes("pods", namespace, labels, dump_requests);
        if(result == null)
            return Collections.emptyList();
        return parseJsonResult(result, namespace, labels);
    }

    protected List<InetAddress> parseJsonResult(String input, String namespace, String labels) {
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
        List<InetAddress> pods=new ArrayList<>();
        for(Json obj: items) {
            if(obj.isObject() && obj.has("status")) {
                Json status=obj.at("status");
                if(status.isObject() && status.has("podIP")) {
                    String podIP=status.at("podIP").asString();
                    if(status.has("phase")) {
                        Json phase=status.at("phase");
                        if(phase != null && phase.isString() && !"Running".equals(phase.asString())) {
                            log.trace("skipped pod with IP=%s as it is not running (%s)", podIP, phase);
                            continue;
                        }
                    }
                    try {
                        InetAddress addr=InetAddress.getByName(podIP);
                        if(!pods.contains(addr))
                            pods.add(addr);
                    }
                    catch(Exception ex) {
                        log.error("failed converting podID to InetAddress", ex);
                    }
                }
            }
        }
        log.trace("getPods(%s, %s) = %s", namespace, labels, pods);
        return pods;
    }
}
