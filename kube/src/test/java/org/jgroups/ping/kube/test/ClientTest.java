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

import org.jgroups.ping.kube.Client;
import org.jgroups.ping.kube.Container;
import org.jgroups.ping.kube.Pod;
import org.jgroups.ping.kube.Port;
import org.jgroups.ping.kube.test.util.ContextBuilder;
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
        Port port = container.getPort("http");
        assertEquals(8080, port.getContainerPort());
    }

    @Test
    public void testAllowEmptyPortNameWithNullPortName() throws Exception {
        //given
        Client client = new TestClient();

        ContextBuilder context = ContextBuilder.newContext().withPingPortName("ping");
        context.withContainer().withUnnamedPort(8888);

        //when
        boolean result = client.accept(context.build());

        //then
        assertEquals(true, result);
    }

    @Test
    public void testAllowEmptyPortNameWithEmptyPortName() throws Exception {
        //given
        Client client = new TestClient();

        ContextBuilder context = ContextBuilder.newContext().withPingPortName("ping");
        context.withContainer().withNamedPort("", 8888);

        //when
        boolean result = client.accept(context.build());

        //then
        assertEquals(true, result);
    }

    @Test
    public void testAllowEmptyPortNameWithWrongName() throws Exception {
        //given
        Client client = new TestClient();

        ContextBuilder context = ContextBuilder.newContext().withPingPortName("ping");
        context.withContainer().withNamedPort("wrongName", 8888);

        //when
        boolean result = client.accept(context.build());

        //then
        assertEquals(false, result);
    }

    @Test
    public void testNotAllowingEmptyPortNames() throws Exception {
        //given
        Client client = new TestClient(false);

        ContextBuilder context = ContextBuilder.newContext().withPingPortName("ping");
        context.withContainer().withUnnamedPort(8888);

        //when
        boolean result = client.accept(context.build());

        //then
        assertEquals(false, result);
    }

}
