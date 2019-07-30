package org.jgroups.protocols.kubernetes;

public class Pod {

   private final String name;
   private final String ip;
   private final String podGroup;    // name of group of Pods during Rolling Update. There is two groups: new pods and old pods
   private final boolean isReady;


   public Pod(String name, String ip, String podGroup, boolean isReady) {
      this.name = name;
      this.ip = ip;
      this.podGroup = podGroup;
      this.isReady = isReady;
   }

   public Pod(String name, String ip, String podGroup) {
      this(name, ip, podGroup, false);
   }

   public String getName() {
      return name;
   }

   public String getIp() {
      return ip;
   }

   public String getPodGroup() {
      return podGroup;
   }

   public boolean isReady() {
      return isReady;
   }

   @Override
   public String toString() {
      return "Pod{" +
            "name='" + name + '\'' +
            ", ip='" + ip + '\'' +
            ", podGroup='" + podGroup + '\'' +
            '}';
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Pod pod = (Pod) o;

      if (name != null ? !name.equals(pod.name) : pod.name != null) return false;
      if (ip != null ? !ip.equals(pod.ip) : pod.ip != null) return false;
      return podGroup != null ? podGroup.equals(pod.podGroup) : pod.podGroup == null;
   }

   @Override
   public int hashCode() {
      int result = name != null ? name.hashCode() : 0;
      result = 31 * result + (ip != null ? ip.hashCode() : 0);
      result = 31 * result + (podGroup != null ? podGroup.hashCode() : 0);
      return result;
   }
}
