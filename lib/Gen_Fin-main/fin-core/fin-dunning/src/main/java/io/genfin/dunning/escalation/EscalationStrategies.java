package io.genfin.dunning.escalation;

import io.genfin.dunning.internal.escalation.DefaultEscalationStrategy;
import io.genfin.dunning.port.escalation.EscalationStrategy;

/**
 * Factory for {@link EscalationStrategy} instances. For anything other than the standard
 * highest-matching-rung ladder lookup, an application implements {@link EscalationStrategy}
 * directly.
 */
public final class EscalationStrategies {

  private static final EscalationStrategy STANDARD = new DefaultEscalationStrategy();

  private EscalationStrategies() {}

  /**
   * Picks the highest {@code EscalationRule} rung whose threshold the attempt count has reached.
   */
  public static EscalationStrategy standard() {
    return STANDARD;
  }
}
