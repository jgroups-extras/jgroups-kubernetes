
package org.jgroups.ping.kube.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.jgroups.protocols.kubernetes.Client;
import org.jgroups.protocols.kubernetes.Pod;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ClientTest {

    @Test
    public void testPods() throws Exception {
        Client client = new TestClient();
        List<Pod> pods = client.getPods(null, null, false);
        Assert.assertNotNull(pods);
        assertEquals(2, pods.size());
        String pod = pods.get(0).getIp();
        Assert.assertNotNull(pod);
    }

    @Test
    public void testParsingPortWithoutNames() throws Exception {
        //given
        Client client = new TestClient("/pods_without_ports.json");

        //when
        int numberOfPods = client.getPods(null, null, false).size();

        //then
        assertEquals(2, numberOfPods);
    }

    @Test
    public void testParsingPodGroupPodTemplateHash() throws Exception {
        //given
        Client client = new TestClient("/pods_without_ports.json");

        //when
        String podGroup = client.getPods(null, null, false).get(0).getPodGroup();

        //then
        assertEquals("infinispan-simple-tutorials-kubernetes-5", podGroup);
    }

    @Test
    public void testParsingPodGroupOpenshift() throws Exception {
        //given
        Client client = new TestClient("/replicaset_rolling_update.json");

        //when
        String podGroup = client.getPods(null, null, false).get(0).getPodGroup();

        //then
        assertEquals("6569c544b", podGroup);
    }


}
