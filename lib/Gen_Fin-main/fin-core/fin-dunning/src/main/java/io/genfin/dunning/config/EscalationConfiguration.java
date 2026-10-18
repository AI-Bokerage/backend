package io.genfin.dunning.config;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.escalation.EscalationPolicies;
import io.genfin.dunning.port.escalation.EscalationPolicy;

/**
 * Immutable, builder-based configuration for the Escalation concern: the resolved {@link
 * EscalationPolicy} (itself composed of an application's escalation ladder). The empty-ladder
 * default below exists only so {@code EscalationConfiguration.builder().build()} compiles and is
 * testable out of the box (no attempt count ever escalates) - resolving the real ladder for a
 * deployment always flows through an application's own {@code DunningPolicy}.
 */
public final class EscalationConfiguration {

  private final EscalationPolicy escalationPolicy;

  private EscalationConfiguration(Builder builder) {
    this.escalationPolicy =
        Validate.notNull(builder.escalationPolicy, "escalationPolicy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public EscalationPolicy escalationPolicy() {
    return escalationPolicy;
  }

  public static final class Builder {

    private EscalationPolicy escalationPolicy = EscalationPolicies.standard();

    public Builder escalationPolicy(EscalationPolicy escalationPolicy) {
      this.escalationPolicy = escalationPolicy;
      return this;
    }

    public EscalationConfiguration build() {
      return new EscalationConfiguration(this);
    }
  }
}
