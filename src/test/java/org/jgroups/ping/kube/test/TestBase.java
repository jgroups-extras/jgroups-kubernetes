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

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Util;
import org.junit.After;
import org.junit.Before;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class TestBase {
    protected static final int    NUM = 2;
    protected static final String CLUSTER_NAME = "test";

    protected JChannel[]   channels;
    protected MyReceiver[] receivers;



    @Before
    public void setUp() throws Exception {
        channels = new JChannel[NUM];
        receivers = new MyReceiver[NUM];

        for(int i = 0; i < NUM; i++) {
            channels[i] = new JChannel(
              new TCP().setValue("bind_addr", InetAddress.getLoopbackAddress()),
              createPing(),
              new NAKACK2(),
              new UNICAST3(),
              new STABLE(),
              new GMS()
            );
            channels[i].setName(Character.toString((char) ('A' + i)));
            channels[i].connect(CLUSTER_NAME);
            channels[i].setReceiver(receivers[i] = new MyReceiver());
        }
    }

    @After
    public void tearDown() throws Exception {
        Util.close(channels);
    }

    protected abstract Protocol createPing();

    protected void clearReceivers() {
        Stream.of(receivers).forEach(r -> r.getList().clear());
    }

    protected static class MyReceiver extends ReceiverAdapter {
        protected final List<Integer> list = new ArrayList<>();

        public List<Integer> getList() {
            return list;
        }

        public void receive(Message msg) {
            synchronized(list) {
                list.add(msg.getObject());
            }
        }
    }
}
