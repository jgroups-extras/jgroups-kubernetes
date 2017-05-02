
package org.jgroups.ping.kube.test;

import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.kubernetes.Client;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static org.jgroups.protocols.kubernetes.Utils.readFileToString;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TestClient extends Client {
    private final Map<String, String> OPS = new HashMap<>();


    public TestClient() throws URISyntaxException, IOException {
        this("/pods.json");
    }

    public TestClient(String jsonFile) throws URISyntaxException, IOException {
        super(null, null, 0, 0, 0, 0,
              null, LogFactory.getLog(TestClient.class));
        String json = readFileToString(new File(TestClient.class.getResource(jsonFile).toURI()));
        OPS.put("pods", json);
    }

    @Override
    protected String fetchFromKubernetes(String op, String namespace, String labels, boolean dump_requests) throws Exception {
        String value = OPS.get(op);
        if (value == null)
            throw new IllegalStateException("No such op: " + op);
        return value;
    }
}
