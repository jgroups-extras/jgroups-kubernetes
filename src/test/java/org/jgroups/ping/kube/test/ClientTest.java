
package org.jgroups.ping.kube.test;

import org.jgroups.protocols.kubernetes.Client;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ClientTest {

    @Test
    public void testPods() throws Exception {
        Client client = new TestClient();
        List<InetAddress> pods = client.getPods(null, null, false);
        Assert.assertNotNull(pods);
        assertEquals(2, pods.size());
        InetAddress pod = pods.get(0);
        Assert.assertNotNull(pod);
    }

    @Test
    public void testParsingPortWithoutNames() throws Exception {
        //given
        Client client = new TestClient("/pods_without_ports.json");

        //when
        long numberOfPods =(long)client.getPods(null, null, false).size();

        //then
        assertEquals(2, numberOfPods);
    }

}
