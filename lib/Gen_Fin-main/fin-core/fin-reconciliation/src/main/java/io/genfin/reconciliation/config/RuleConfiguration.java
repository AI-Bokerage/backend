package io.genfin.reconciliation.config;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.port.rule.RuleEngine;
import java.util.List;

/** The rule-evaluation policy set for a Reconciliation-engine deployment. */
public final class RuleConfiguration {

  private final RuleEngine engine;
  private final List<ReconciliationRule> rules;

  private RuleConfiguration(Builder builder) {
    this.engine = Validate.notNull(builder.engine, "engine must not be null.");
    this.rules = List.copyOf(Validate.notNull(builder.rules, "rules must not be null."));
  }

  public static Builder builder() {
    return new Builder();
  }

  public RuleEngine engine() {
    return engine;
  }

  public List<ReconciliationRule> rules() {
    return rules;
  }

  public static final class Builder {

    private RuleEngine engine;
    private List<ReconciliationRule> rules = List.of();

    public Builder engine(RuleEngine engine) {
      this.engine = engine;
      return this;
    }

    public Builder rules(List<ReconciliationRule> rules) {
      this.rules = rules;
      return this;
    }

    public RuleConfiguration build() {
      return new RuleConfiguration(this);
    }
  }
}
