
package org.jgroups.ping.kube.test;

import org.jboss.dmr.ModelNode;
import org.jgroups.logging.LogFactory;
import org.jgroups.ping.kube.Client;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static org.jgroups.ping.common.Utils.readFileToString;

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
    protected ModelNode getNode(String op, String namespace, String labels) throws Exception {
        String value = OPS.get(op);
        if (value == null) {
            throw new IllegalStateException("No such op: " + op);
        }
        return ModelNode.fromJSONString(value);
    }
}
