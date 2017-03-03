package org.openshift.ping.common.compatibility;

import org.jgroups.Version;

/**
 * A small set of compatibility checking utils
 *
 * @author Sebastian Łaskawiec
 */
public class CompatibilityUtils {

   private CompatibilityUtils() {
   }

   /**
    * @return <code>true</code> when JGroups 4 is on the classpath. <code>false</code> otherwise.
    */
   public static boolean isJGroups4() {
      return Version.major == 4;
   }
}
