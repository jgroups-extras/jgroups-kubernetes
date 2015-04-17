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

package org.openshift.ping.kube.test;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.openshift.ping.kube.Client;
import org.openshift.ping.kube.Container;
import org.openshift.ping.kube.Pod;
import org.openshift.ping.kube.Port;
import org.openshift.ping.kube.test.support.TestClient;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ClientTest {

    @Test
    public void testPods() throws Exception {
        Client client = new TestClient();
        List<Pod> pods = client.getPods();
        Assert.assertNotNull(pods);
        Assert.assertEquals(2, pods.size());
        Pod pod = pods.get(0);
        Assert.assertEquals("host-1", pod.getHost());
        Assert.assertNotNull(pod.getContainers());
        Assert.assertEquals(1, pod.getContainers().size());
        Container container = pod.getContainers().get(0);
        Assert.assertNotNull(container.getPorts());
        Assert.assertEquals(1, container.getPorts().size());
        Port port = container.getPort("output");
        Assert.assertEquals(8080, port.getHostPort().intValue());
        Assert.assertEquals(80, port.getContainerPort().intValue());
    }

}
