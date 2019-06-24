package org.jgroups.ping.kube.test;

import mjson.Json;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.jgroups.protocols.kubernetes.Utils.readFileToString;
import static org.junit.Assert.assertEquals;

/**
 * @author Bela Ban
 * @since x.y
 */
public class JsonTest {

    @Test
    public void testJsonParser() throws Exception {
        String input=readFileToString(new File(TestClient.class.getResource("/pods_without_ports.json").toURI()));

        Json json=Json.read(input);

        List<Json> items=json.at("items").asJsonList();

        for(Json obj: items) {
            Json status=obj.at("status");
            Json phase=status.at("phase");
            Json ip=status.at("podIP");
            System.out.printf("%s -> %s\n", ip, phase);
            assertEquals("Running", phase.asString());
        }

        /* JsonObject root=JsonParser.object().from(input);
        JsonArray items=root.getArray("items");

        for(int i=0; i < items.size(); i++) {
            JsonObject obj=items.getObject(i);
            JsonObject status=obj.getObject("status");
            String hostIP=status.getString("hostIP");
            System.out.println("hostIP = " + hostIP);
        }*/
    }
}
