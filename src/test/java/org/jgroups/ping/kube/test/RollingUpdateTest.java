package org.jgroups.ping.kube.test;

import org.assertj.core.api.Assertions;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.kubernetes.KUBE_PING;
import org.jgroups.protocols.kubernetes.Pod;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.stack.IpAddress;
import org.junit.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jgroups.ping.kube.test.util.FreePortFinder.findFreePort;

/**
 * Tests Rolling update scenarios.
 *
 * <p>
 *    The idea of this tests is to mock the Kubernetes API response and check which hosts were queried during
 *    initial discovery. This way we will know which hosts where put into the cluster.
 * </p>
 */
public class RollingUpdateTest {

   @Test
   public void testPuttingAllNodesInTheSameClusterDuringRollingUpdate() throws Exception {
      //given
      KUBE_PING_FOR_TESTING testedProtocol = new KUBE_PING_FOR_TESTING("/openshift_rolling_update.json");

      //when
      sendInitialDiscovery(testedProtocol);
      Set<String> membersUsedForDiscovery = testedProtocol.getCollectedMessages().stream()
            .map(e -> ((IpAddress)e.getDest()).getIpAddress().getHostAddress())
            .collect(Collectors.toSet());
      List<String> allPodsFromKubernetesApi = testedProtocol.getPods().stream()
            .map(Pod::getIp)
            .collect(Collectors.toList());

      //then
      Assertions.assertThat(membersUsedForDiscovery).hasSameElementsAs(allPodsFromKubernetesApi);
   }

   @Test
   public void testPutOnlyNodesWithTheSameParentDuringRollingUpdate() throws Exception {
      //given
      KUBE_PING_FOR_TESTING testedProtocol = new KUBE_PING_FOR_TESTING("/openshift_rolling_update.json");
      testedProtocol.setValue("split_clusters_during_rolling_update", true);

      //when
      sendInitialDiscovery(testedProtocol);
      String senderParentDeployment = testedProtocol.getPods().stream()
            .filter(pod -> "127.0.0.1".equals(pod.getIp()))
            .map(Pod::getParentDeployment)
            .findFirst().get();
      Set<String> membersUsedForDiscovery = testedProtocol.getCollectedMessages().stream()
            .map(e -> ((IpAddress)e.getDest()).getIpAddress().getHostAddress())
            .collect(Collectors.toSet());
      List<String> allowedPodsFromKubernetesApi = testedProtocol.getPods().stream()
            .filter(pod -> senderParentDeployment.equals(pod.getParentDeployment()))
            .map(Pod::getIp)
            .collect(Collectors.toList());

      //then
      Assertions.assertThat(allowedPodsFromKubernetesApi).containsAll(membersUsedForDiscovery);
   }

   private static void sendInitialDiscovery(KUBE_PING kubePingProtocol) throws Exception {
      new JChannel(
            new TCP().setValue("bind_addr", InetAddress.getLoopbackAddress()).setValue("bind_port", findFreePort()),
            kubePingProtocol,
            new NAKACK2(),
            new GMS().setValue("join_timeout", 1)
      ).connect("RollingUpdateTest").disconnect();
   }

   static class KUBE_PING_FOR_TESTING extends KUBE_PING {

      private final String resourceFile;
      private final List<Message> collectedMessages = new ArrayList<>();
      private List<Pod> pods;

      public KUBE_PING_FOR_TESTING(String resourceFile) {
         this.resourceFile = resourceFile;
      }

      @Override
      public void init() throws Exception {
         super.init();
         client = new TestClient(resourceFile);
         pods = client.getPods(namespace, labels, true);
      }

      @Override
      protected void sendDiscoveryRequest(Message req) {
         collectedMessages.add(req);
      }

      public List<Message> getCollectedMessages() {
         return collectedMessages;
      }

      public List<Pod> getPods() {
         return pods;
      }
   }

}
