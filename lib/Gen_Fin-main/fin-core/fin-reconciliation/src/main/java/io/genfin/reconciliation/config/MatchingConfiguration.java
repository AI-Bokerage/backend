package io.genfin.reconciliation.config;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.port.matching.MatchingEngine;
import io.genfin.reconciliation.port.matching.MatchingPolicy;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import java.util.List;

/** The matching policy set for a Reconciliation-engine deployment. */
public final class MatchingConfiguration {

  private final MatchingEngine engine;
  private final MatchingPolicy policy;
  private final List<MatchingStrategy> strategies;

  private MatchingConfiguration(Builder builder) {
    this.engine = Validate.notNull(builder.engine, "engine must not be null.");
    this.policy = Validate.notNull(builder.policy, "policy must not be null.");
    this.strategies =
        List.copyOf(Validate.notNull(builder.strategies, "strategies must not be null."));
  }

  public static Builder builder() {
    return new Builder();
  }

  public MatchingEngine engine() {
    return engine;
  }

  public MatchingPolicy policy() {
    return policy;
  }

  public List<MatchingStrategy> strategies() {
    return strategies;
  }

  public static final class Builder {

    private MatchingEngine engine;
    private MatchingPolicy policy;
    private List<MatchingStrategy> strategies = List.of();

    public Builder engine(MatchingEngine engine) {
      this.engine = engine;
      return this;
    }

    public Builder policy(MatchingPolicy policy) {
      this.policy = policy;
      return this;
    }

    public Builder strategies(List<MatchingStrategy> strategies) {
      this.strategies = strategies;
      return this;
    }

    public MatchingConfiguration build() {
      return new MatchingConfiguration(this);
    }
  }
}
