package org.jgroups.protocols.kubernetes;

public class Pod {

   private final String name;
   private final String ip;
   private final String parentDeployment;


   public Pod(String name, String ip, String parentDeployment) {
      this.name = name;
      this.ip = ip;
      this.parentDeployment = parentDeployment;
   }

   public String getName() {
      return name;
   }

   public String getIp() {
      return ip;
   }

   public String getParentDeployment() {
      return parentDeployment;
   }

   @Override
   public String toString() {
      return "Pod{" +
            "name='" + name + '\'' +
            ", ip='" + ip + '\'' +
            ", parentDeployment='" + parentDeployment + '\'' +
            '}';
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Pod pod = (Pod) o;

      if (name != null ? !name.equals(pod.name) : pod.name != null) return false;
      if (ip != null ? !ip.equals(pod.ip) : pod.ip != null) return false;
      return parentDeployment != null ? parentDeployment.equals(pod.parentDeployment) : pod.parentDeployment == null;
   }

   @Override
   public int hashCode() {
      int result = name != null ? name.hashCode() : 0;
      result = 31 * result + (ip != null ? ip.hashCode() : 0);
      result = 31 * result + (parentDeployment != null ? parentDeployment.hashCode() : 0);
      return result;
   }
}
