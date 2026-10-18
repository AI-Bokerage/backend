package io.genfin.pricing.config;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.credit.CreditPolicy;
import io.genfin.pricing.port.credit.CreditRegistry;

/** The Credit Engine policy set for a fin-pricing deployment. */
public final class CreditConfiguration {

  private final CreditRegistry creditRegistry;
  private final CreditPolicy creditPolicy;

  private CreditConfiguration(Builder builder) {
    this.creditRegistry =
        Validate.notNull(builder.creditRegistry, "creditRegistry must not be null.");
    this.creditPolicy = Validate.notNull(builder.creditPolicy, "creditPolicy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public CreditRegistry creditRegistry() {
    return creditRegistry;
  }

  public CreditPolicy creditPolicy() {
    return creditPolicy;
  }

  public static final class Builder {

    private CreditRegistry creditRegistry;
    private CreditPolicy creditPolicy;

    public Builder creditRegistry(CreditRegistry creditRegistry) {
      this.creditRegistry = creditRegistry;
      return this;
    }

    public Builder creditPolicy(CreditPolicy creditPolicy) {
      this.creditPolicy = creditPolicy;
      return this;
    }

    public CreditConfiguration build() {
      return new CreditConfiguration(this);
    }
  }
}
