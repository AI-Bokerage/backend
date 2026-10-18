package io.genfin.ledger.config;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.port.period.PeriodCalculator;
import io.genfin.ledger.port.period.PeriodPolicy;

/** The accounting-period policy set for a Ledger-engine deployment. */
public final class PeriodConfiguration {

  private final PeriodPolicy policy;
  private final PeriodCalculator calculator;

  private PeriodConfiguration(Builder builder) {
    this.policy = Validate.notNull(builder.policy, "policy must not be null.");
    this.calculator = Validate.notNull(builder.calculator, "calculator must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public PeriodPolicy policy() {
    return policy;
  }

  public PeriodCalculator calculator() {
    return calculator;
  }

  public static final class Builder {

    private PeriodPolicy policy;
    private PeriodCalculator calculator;

    public Builder policy(PeriodPolicy policy) {
      this.policy = policy;
      return this;
    }

    public Builder calculator(PeriodCalculator calculator) {
      this.calculator = calculator;
      return this;
    }

    public PeriodConfiguration build() {
      return new PeriodConfiguration(this);
    }
  }
}
