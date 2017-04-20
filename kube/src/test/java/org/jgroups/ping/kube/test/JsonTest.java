package org.jgroups.ping.kube.test;

import org.junit.Test;

import java.io.File;

import static org.jgroups.ping.common.Utils.readFileToString;

/**
 * @author Bela Ban
 * @since x.y
 */
public class JsonTest {

    @Test
    public void testJsonParser() throws Exception {
        String input=readFileToString(new File(TestClient.class.getResource("/pods_without_ports.json").toURI()));


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
