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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.jgroups.JChannel;
import org.jgroups.View;
import org.jgroups.util.Util;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public abstract class PingTestBase extends TestBase {

    @Test
    public void testCluster() throws Exception {
        doTestCluster();
    }

    @Test
    public void testRestart() throws Exception {
        doTestCluster();

        channels[getNum() - 1].disconnect();
        channels[getNum() - 1].connect(CLUSTER_NAME);

        doTestCluster();
    }

    protected void doTestCluster() throws Exception {
        waitUntilAllChannelsHaveSameView(10000, 1000, channels);

        // Tests unicasts from the first to the last
        JChannel first = channels[0], last = channels[getNum() - 1];
        for (int i = 1; i <= 10; i++) {
            first.send(last.getAddress(), i);
        }

        List<Integer> msgs = receivers[getNum() - 1].getList();
        Util.waitUntilListHasSize(msgs, 10, 10000, 1000);
        System.out.println("msgs = " + msgs);
        for (int i = 1; i < 10; i++) {
            Assert.assertTrue(msgs.contains(i));
        }

        clearReceivers();

        // Tests multicasts
        for (int i = 0; i < getNum(); i++) {
            JChannel ch = channels[i];
            int num = (i + 1) * 10;
            for (int j = 0; j <= 5; j++) {
                ch.send(null, num + j);
            }
        }

        final int expected_size = getNum() * 6;
        final List<Integer> expected_numbers = new ArrayList<>(expected_size);
        for (int i = 0; i < getNum(); i++) {
            int num = (i + 1) * 10;
            for (int j = 0; j <= 5; j++) {
                expected_numbers.add(num + j);
            }
        }

        for (int i = 0; i < getNum(); i++) {
            List<Integer> list = receivers[i].getList();
            Util.waitUntilListHasSize(list, expected_size, 10000, 1000);
            System.out.println("list[" + i + "]: " + list);
        }

        for (int i = 0; i < getNum(); i++) {
            List<Integer> list = receivers[i].getList();
            for (int num : expected_numbers) {
                Assert.assertTrue(list.contains(num));
            }
        }

        clearReceivers();
    }

    /**
     * This method has been copied from JGroups. It changed name a couple of times, so we should
     * have a safety copy here...
     */
    public static void waitUntilAllChannelsHaveSameView(long timeout, long interval, JChannel... channels) throws TimeoutException {
        if(interval >= timeout || timeout <= 0)
            throw new IllegalArgumentException("interval needs to be smaller than timeout or timeout needs to be > 0");
        long target_time=System.currentTimeMillis() + timeout;
        while(System.currentTimeMillis() <= target_time) {
            boolean all_channels_have_correct_view=true;
            View first=channels[0].getView();
            for(JChannel ch : channels) {
                View view=ch.getView();
                if(!Objects.equals(view, first) || view.size() != channels.length) {
                    all_channels_have_correct_view=false;
                    break;
                }
            }
            if(all_channels_have_correct_view)
                return;
            Util.sleep(interval);
        }
        View[] views=new View[channels.length];
        StringBuilder sb=new StringBuilder();
        for(int i=0; i < channels.length; i++) {
            views[i]=channels[i].getView();
            sb.append(channels[i].getName()).append(": ").append(views[i]).append("\n");
        }
        View first=channels[0].getView();
        for(View view : views)
            if(!Objects.equals(view, first))
                throw new TimeoutException("Timeout " + timeout + " kicked in, views are:\n" + sb);
    }

}
