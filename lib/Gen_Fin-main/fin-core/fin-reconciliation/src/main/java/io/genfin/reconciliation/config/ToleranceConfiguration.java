package io.genfin.reconciliation.config;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.port.tolerance.CustomToleranceRuleRegistry;
import io.genfin.reconciliation.port.tolerance.ToleranceCalculator;

/** The tolerance-evaluation policy set for a Reconciliation-engine deployment. */
public final class ToleranceConfiguration {

  private final ToleranceCalculator calculator;
  private final CustomToleranceRuleRegistry customRules;

  private ToleranceConfiguration(Builder builder) {
    this.calculator = Validate.notNull(builder.calculator, "calculator must not be null.");
    this.customRules = Validate.notNull(builder.customRules, "customRules must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public ToleranceCalculator calculator() {
    return calculator;
  }

  public CustomToleranceRuleRegistry customRules() {
    return customRules;
  }

  public static final class Builder {

    private ToleranceCalculator calculator;
    private CustomToleranceRuleRegistry customRules;

    public Builder calculator(ToleranceCalculator calculator) {
      this.calculator = calculator;
      return this;
    }

    public Builder customRules(CustomToleranceRuleRegistry customRules) {
      this.customRules = customRules;
      return this;
    }

    public ToleranceConfiguration build() {
      return new ToleranceConfiguration(this);
    }
  }
}
