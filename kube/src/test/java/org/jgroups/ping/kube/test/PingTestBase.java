
package org.jgroups.ping.kube.test;

import org.jgroups.JChannel;
import org.jgroups.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.jgroups.util.Util.waitUntilAllChannelsHaveSameView;

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

        channels[NUM - 1].disconnect();
        channels[NUM - 1].connect(CLUSTER_NAME);

        doTestCluster();
    }

    protected void doTestCluster() throws Exception {
        waitUntilAllChannelsHaveSameView(10000, 1000, channels);

        // Tests unicasts from the first to the last
        JChannel first = channels[0], last = channels[NUM - 1];
        for (int i = 1; i <= 10; i++) {
            first.send(last.getAddress(), i);
        }

        List<Integer> msgs = receivers[NUM - 1].getList();
        Util.waitUntilListHasSize(msgs, 10, 10000, 1000);
        System.out.println("msgs = " + msgs);
        for (int i = 1; i < 10; i++) {
            Assert.assertTrue(msgs.contains(i));
        }

        clearReceivers();

        // Tests multicasts
        for (int i = 0; i < NUM; i++) {
            JChannel ch = channels[i];
            int num = (i + 1) * 10;
            for (int j = 0; j <= 5; j++) {
                ch.send(null, num + j);
            }
        }

        final int expected_size = NUM * 6;
        final List<Integer> expected_numbers = new ArrayList<>(expected_size);
        for (int i = 0; i < NUM; i++) {
            int num = (i + 1) * 10;
            for (int j = 0; j <= 5; j++) {
                expected_numbers.add(num + j);
            }
        }

        for (int i = 0; i < NUM; i++) {
            List<Integer> list = receivers[i].getList();
            Util.waitUntilListHasSize(list, expected_size, 10000, 1000);
            System.out.println("list[" + i + "]: " + list);
        }

        for (int i = 0; i < NUM; i++) {
            List<Integer> list = receivers[i].getList();
            for (int num : expected_numbers) {
                Assert.assertTrue(list.contains(num));
            }
        }

        clearReceivers();
    }

}
