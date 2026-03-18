package org.jgroups.ping.kube.test;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.junit.Test;

import java.io.File;
import java.io.StringReader;

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

        JsonObject json;
        try (JsonReader reader = Json.createReader(new StringReader(input))) {
            json = reader.readObject();
        }

        JsonArray items=json.getJsonArray("items");

        for(JsonValue item: items) {
            JsonObject obj=item.asJsonObject();
            JsonObject status=obj.getJsonObject("status");
            String phase=status.getString("phase");
            String ip=status.getString("podIP");
            System.out.printf("%s -> %s\n", ip, phase);
            assertEquals("Running", phase);
        }
    }
}
