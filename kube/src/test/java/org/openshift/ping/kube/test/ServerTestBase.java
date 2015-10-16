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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.PingData;
import org.jgroups.protocols.PingHeader;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Buffer;
import org.jgroups.util.Streamable;
import org.jgroups.util.UUID;
import org.jgroups.util.Util;
import org.junit.Assert;
import org.junit.Test;
import org.openshift.ping.common.server.Server;
import org.openshift.ping.kube.Client;
import org.openshift.ping.kube.KubePing;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class ServerTestBase extends TestBase {
    
    private TestKubePing pinger;

    @Override
    protected int getNum() {
        return 1;
    }

    protected Protocol createPing() {
        KubePing ping = new TestKubePing();
        ping.setMasterProtocol("http");
        ping.setMasterHost("localhost");
        ping.setMasterPort(8080);
        ping.setNamespace("default");
        applyConfig(ping);
        pinger = (TestKubePing) ping;
        return ping;
    }

    protected abstract void applyConfig(KubePing ping);

    @Test
    public void testResponse() throws Exception {
        Address local_addr = pinger.getLocalAddress();
        PhysicalAddress physical_addr = (PhysicalAddress) pinger
                .down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));
        PingHeader hdr = new TestPingHeader();
        PingData data = new PingData(local_addr, null, false, UUID.get(local_addr),
                physical_addr != null ? Arrays.asList(physical_addr) : null);
        Message msg = new Message(null).setFlag(Message.Flag.DONT_BUNDLE)
                .putHeader(pinger.getId(), hdr).setBuffer(streamableToBuffer(data));

        URL url = new URL("http://localhost:8888");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.addRequestProperty(Server.CLUSTER_NAME, TestBase.CLUSTER_NAME);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        msg.writeTo(out);
        out.flush();

        Assert.assertEquals(200, conn.getResponseCode());
    }

    private static Buffer streamableToBuffer(Streamable obj) {
        final ByteArrayOutputStream out_stream = new ByteArrayOutputStream(512);
        DataOutputStream out = new DataOutputStream(out_stream);
        try {
            Util.writeStreamable(obj, out);
            return new Buffer(out_stream.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    private static final class TestKubePing extends KubePing {
        static {
            ClassConfigurator.addProtocol(JGROUPS_KUBE_PING_ID, TestKubePing.class);
        }

        protected Address getLocalAddress() {
            return local_addr;
        }

        @Override
        protected Client getClient() {
            return new TestClient();
        }
    }
    
    private static final class TestPingHeader extends PingHeader {
        private TestPingHeader() {
            cluster_name = TestBase.CLUSTER_NAME;
            type = PingHeader.GET_MBRS_REQ;
        }
    }
}
