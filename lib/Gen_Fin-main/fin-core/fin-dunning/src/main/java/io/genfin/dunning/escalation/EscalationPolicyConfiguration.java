package io.genfin.dunning.escalation;

import java.util.List;

/**
 * Immutable, builder-based configuration for an {@code EscalationPolicy}. The empty default rule
 * list below exists only so {@code EscalationPolicyConfiguration.builder().build()} compiles and is
 * testable out of the box (an obligation with no rules simply never escalates) - resolving the real
 * ladder for a deployment always flows through an application's own {@code DunningPolicy}, never a
 * literal baked in here.
 */
public final class EscalationPolicyConfiguration {

  private final List<EscalationRule> escalationRules;

  private EscalationPolicyConfiguration(Builder builder) {
    this.escalationRules = List.copyOf(builder.escalationRules);
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<EscalationRule> escalationRules() {
    return escalationRules;
  }

  public static final class Builder {

    private List<EscalationRule> escalationRules = List.of();

    public Builder escalationRules(List<EscalationRule> escalationRules) {
      this.escalationRules = escalationRules;
      return this;
    }

    public EscalationPolicyConfiguration build() {
      return new EscalationPolicyConfiguration(this);
    }
  }
}
