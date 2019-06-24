package org.jgroups.ping.kube.test.util;

import java.net.ServerSocket;

public class FreePortFinder {

   private static final int DEFAULT_PORT = 13256;

   private FreePortFinder() { }

   public static int findFreePort() {
      try (ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      } catch (Exception e) {
         return DEFAULT_PORT;
      }
   }

}
