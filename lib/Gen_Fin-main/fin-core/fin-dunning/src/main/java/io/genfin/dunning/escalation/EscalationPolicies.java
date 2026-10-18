package io.genfin.dunning.escalation;

import io.genfin.dunning.internal.escalation.DefaultEscalationPolicy;
import io.genfin.dunning.port.escalation.EscalationPolicy;
import java.util.List;

/** Factory for {@link EscalationPolicy} instances. */
public final class EscalationPolicies {

  private EscalationPolicies() {}

  public static EscalationPolicy of(EscalationPolicyConfiguration configuration) {
    return new DefaultEscalationPolicy(configuration.escalationRules());
  }

  public static EscalationPolicy of(List<EscalationRule> escalationRules) {
    return new DefaultEscalationPolicy(escalationRules);
  }

  /**
   * A policy built from a fully-defaulted example configuration - see {@link
   * EscalationPolicyConfiguration.Builder}. Carries no rules, so every decision resolves to no
   * escalation until an application supplies its own ladder.
   */
  public static EscalationPolicy standard() {
    return of(EscalationPolicyConfiguration.builder().build());
  }
}
