/**
 *  Copyright 2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.jgroups.ping.kube.test;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Optional;

import org.jgroups.ping.kube.Client;
import org.jgroups.ping.kube.Container;
import org.jgroups.ping.kube.Pod;
import org.jgroups.ping.kube.Port;
import org.jgroups.ping.kube.test.util.ContainerBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ClientTest {

    @Test
    public void testPods() throws Exception {
        Client client = new TestClient();
        List<Pod> pods = client.getPods(null, null);
        Assert.assertNotNull(pods);
        assertEquals(2, pods.size());
        Pod pod = pods.get(0);
        Assert.assertNotNull(pod.getContainers());
        assertEquals(1, pod.getContainers().size());
        Container container = pod.getContainers().get(0);
        Assert.assertNotNull(container.getPorts());
        assertEquals(2, container.getPorts().size());
        Port port = container.getPorts().get(0);
        assertEquals(8080, port.getContainerPort());
    }

    @Test
    public void testParsingPortWithoutNames() throws Exception {
        //given
        Client client = new TestClient("/pods_without_ports.json", 8888);

        //when
        long numberOfPods = client.getPods(null, null).stream()
                .map(port -> port.getPodIP())
                .count();

        //then
        assertEquals(2, numberOfPods);
    }

    @Test
    public void testAcceptingProperPort() throws Exception {
        //given
        Client client = new TestClient();

        //when
        boolean accepted = client.accept(new Port(Optional.empty(), 8888));

        //then
        assertEquals(true, accepted);
    }

    @Test
    public void testRejectingWrongPort() throws Exception {
        //given
        Client client = new TestClient();

        //when
        boolean accepted = client.accept(new Port(Optional.empty(), 1));

        //then
        assertEquals(false, accepted);
    }

    @Test
    public void testAcceptingProperContainer() throws Exception {
        //given
        Client client = new TestClient();

        Container container = ContainerBuilder.newContainer().withPort(8888).build();

        //when
        boolean accepted = client.accept(container);

        //then
        assertEquals(true, accepted);
    }

    @Test
    public void testRejectingWrongContainer() throws Exception {
        //given
        Client client = new TestClient();

        Container container = ContainerBuilder.newContainer().withPort(1234).build();

        //when
        boolean accepted = client.accept(container);

        //then
        assertEquals(false, accepted);
    }
}
