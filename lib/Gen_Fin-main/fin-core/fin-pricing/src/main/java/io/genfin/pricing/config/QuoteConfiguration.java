package io.genfin.pricing.config;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.quote.QuotePolicy;

/** The Quote Engine policy set for a fin-pricing deployment. */
public final class QuoteConfiguration {

  private final QuotePolicy quotePolicy;

  private QuoteConfiguration(Builder builder) {
    this.quotePolicy = Validate.notNull(builder.quotePolicy, "quotePolicy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public QuotePolicy quotePolicy() {
    return quotePolicy;
  }

  public static final class Builder {

    private QuotePolicy quotePolicy;

    public Builder quotePolicy(QuotePolicy quotePolicy) {
      this.quotePolicy = quotePolicy;
      return this;
    }

    public QuoteConfiguration build() {
      return new QuoteConfiguration(this);
    }
  }
}
