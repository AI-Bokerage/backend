package io.genfin.pricing.config;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.rule.CommercialRuleEngine;
import io.genfin.pricing.port.rule.CommercialRuleRegistry;
import io.genfin.pricing.rule.RuleContext;

/** The Commercial Rule Engine policy set for a fin-pricing deployment. */
public final class CommercialRuleConfiguration {

  private final CommercialRuleRegistry commercialRuleRegistry;
  private final CommercialRuleEngine commercialRuleEngine;
  private final RuleContext ruleContext;

  private CommercialRuleConfiguration(Builder builder) {
    this.commercialRuleRegistry =
        Validate.notNull(
            builder.commercialRuleRegistry, "commercialRuleRegistry must not be null.");
    this.commercialRuleEngine =
        Validate.notNull(builder.commercialRuleEngine, "commercialRuleEngine must not be null.");
    this.ruleContext = Validate.notNull(builder.ruleContext, "ruleContext must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public CommercialRuleRegistry commercialRuleRegistry() {
    return commercialRuleRegistry;
  }

  public CommercialRuleEngine commercialRuleEngine() {
    return commercialRuleEngine;
  }

  public RuleContext ruleContext() {
    return ruleContext;
  }

  public static final class Builder {

    private CommercialRuleRegistry commercialRuleRegistry;
    private CommercialRuleEngine commercialRuleEngine;
    private RuleContext ruleContext;

    public Builder commercialRuleRegistry(CommercialRuleRegistry commercialRuleRegistry) {
      this.commercialRuleRegistry = commercialRuleRegistry;
      return this;
    }

    public Builder commercialRuleEngine(CommercialRuleEngine commercialRuleEngine) {
      this.commercialRuleEngine = commercialRuleEngine;
      return this;
    }

    public Builder ruleContext(RuleContext ruleContext) {
      this.ruleContext = ruleContext;
      return this;
    }

    public CommercialRuleConfiguration build() {
      return new CommercialRuleConfiguration(this);
    }
  }
}
