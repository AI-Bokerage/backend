package io.genfin.dunning.port.backoff;

import io.genfin.api.port.spi.Extension;
import java.time.Duration;

/**
 * SPI computing the delay before retry attempt number {@code attemptNumber}, measured from the
 * originating reference instant (e.g. the obligation's due date or first failure). fin-dunning
 * ships fixed/linear/exponential/fibonacci example implementations, but this interface itself is
 * the plug-in point - an application implements it directly to supply any other custom strategy,
 * there is no separate "CustomBackoff" marker type.
 */
@FunctionalInterface
public interface BackoffStrategy extends Extension {

  /**
   * Returns the delay from the reference instant to that attempt's candidate instant, before any
   * business-calendar adjustment.
   *
   * @param attemptNumber the 1-based retry attempt number.
   */
  Duration nextDelay(int attemptNumber);
}
