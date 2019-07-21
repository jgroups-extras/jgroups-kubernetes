package org.jgroups.ping.kube.test;

import java.util.List;

import org.jgroups.protocols.kubernetes.Client;
import org.jgroups.protocols.kubernetes.Pod;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

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
        assertEquals(3, pods.size());
        assertTrue(pods.get(0).isReady());
        assertTrue(pods.get(1).isReady());
        assertFalse(pods.get(2).isReady());
        String pod = pods.get(0).getIp();
        Assert.assertNotNull(pod);
    }
}
