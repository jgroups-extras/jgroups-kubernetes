package org.openshift.ping.common.compatibility;

/**
 * Thrown on incompatibility errors
 *
 * @author Sebastian Łaskawiec
 */
public class CompatibilityException extends  RuntimeException {

   public CompatibilityException() {
   }

   public CompatibilityException(String message) {
      super(message);
   }

   public CompatibilityException(String message, Throwable cause) {
      super(message, cause);
   }

   public CompatibilityException(Throwable cause) {
      super(cause);
   }

   public CompatibilityException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
   }
}
