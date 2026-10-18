package io.genfin.ledger.config;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.port.balance.BalanceCalculator;
import io.genfin.ledger.port.balance.BalancePolicy;

/** The balance-computation policy set for a Ledger-engine deployment. */
public final class BalanceConfiguration {

  private final BalancePolicy policy;
  private final BalanceCalculator calculator;

  private BalanceConfiguration(Builder builder) {
    this.policy = Validate.notNull(builder.policy, "policy must not be null.");
    this.calculator = Validate.notNull(builder.calculator, "calculator must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public BalancePolicy policy() {
    return policy;
  }

  public BalanceCalculator calculator() {
    return calculator;
  }

  public static final class Builder {

    private BalancePolicy policy;
    private BalanceCalculator calculator;

    public Builder policy(BalancePolicy policy) {
      this.policy = policy;
      return this;
    }

    public Builder calculator(BalanceCalculator calculator) {
      this.calculator = calculator;
      return this;
    }

    public BalanceConfiguration build() {
      return new BalanceConfiguration(this);
    }
  }
}
