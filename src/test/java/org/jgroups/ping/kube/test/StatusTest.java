package org.jgroups.ping.kube.test;

import static org.jgroups.protocols.kubernetes.Utils.readFileToString;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;

import org.jgroups.protocols.kubernetes.Client;
import org.jgroups.protocols.kubernetes.Pod;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:ulrich.romahn@gmail.com">Ulrich Romahn</a>
 */
public class StatusTest {

    @Test
    public void testPodsRunning() throws Exception {
        Client client = new TestClient("/complex_pods.json");
        List<Pod> pods = client.getPods(null, null, false);
        Assert.assertNotNull(pods);
        assertEquals(4, pods.size());
        String pod = pods.get(0).getIp();
        Assert.assertNotNull(pod);
    }
    
    @Test
    public void testOnePodNotRunning() throws Exception {
        final String jsonFile = "/unknown_pods.json";
        Client client = new TestClient(jsonFile);
        List<Pod> pods = client.getPods(null, null, false);
        Assert.assertNotNull(pods);
        assertEquals(2, pods.size());
        String pod = pods.get(0).getIp();
        Assert.assertNotNull(pod);
    }
}
