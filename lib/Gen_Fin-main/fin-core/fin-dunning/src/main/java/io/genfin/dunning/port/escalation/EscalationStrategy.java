package io.genfin.dunning.port.escalation;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.escalation.EscalationDecision;

/**
 * SPI deciding whether an obligation's current failed-attempt count warrants escalation, and if so
 * to which {@code EscalationLevel}/{@code EscalationAction}, per the given {@link EscalationPolicy}
 * ladder. Decides only - never notifies a manager, suspends a service, freezes an account, writes
 * off the obligation, or performs any other action.
 */
@FunctionalInterface
public interface EscalationStrategy extends Extension {

  /**
   * Decides the escalation outcome for an obligation.
   *
   * @param attemptCount the obligation's current failed-attempt count (e.g. the number of {@code
   *     RetryHistory} attempts already failed) the decision is evaluated against.
   * @param policy the resolved policy ladder to decide against.
   */
  EscalationDecision decide(int attemptCount, EscalationPolicy policy);
}
