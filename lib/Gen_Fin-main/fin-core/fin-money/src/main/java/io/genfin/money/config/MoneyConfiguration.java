package io.genfin.money.config;

import io.genfin.api.validation.Validate;
import io.genfin.money.arithmetic.ArithmeticPolicies;
import io.genfin.money.arithmetic.ArithmeticPolicy;
import io.genfin.money.conversion.ConversionPolicy;
import io.genfin.money.currency.Currency;
import io.genfin.money.format.FormattingPolicy;
import io.genfin.money.port.allocation.AllocationStrategy;
import io.genfin.money.tax.TaxConfiguration;

/**
 * The full, explicit policy set for a Money-engine deployment. Nothing here defaults to a hardcoded
 * rule.
 */
public final class MoneyConfiguration {

  private final Currency defaultCurrency;
  private final ArithmeticPolicy arithmeticPolicy;
  private final FormattingPolicy formattingPolicy;
  private final ConversionPolicy conversionPolicy;
  private final AllocationStrategy defaultAllocation;
  private final TaxConfiguration taxConfiguration;

  private MoneyConfiguration(Builder builder) {
    this.defaultCurrency =
        Validate.notNull(builder.defaultCurrency, "defaultCurrency must not be null.");
    this.arithmeticPolicy =
        Validate.notNull(builder.arithmeticPolicy, "arithmeticPolicy must not be null.");
    this.formattingPolicy =
        Validate.notNull(builder.formattingPolicy, "formattingPolicy must not be null.");
    this.conversionPolicy = builder.conversionPolicy;
    this.defaultAllocation =
        Validate.notNull(builder.defaultAllocation, "defaultAllocation must not be null.");
    this.taxConfiguration =
        Validate.notNull(builder.taxConfiguration, "taxConfiguration must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public Currency defaultCurrency() {
    return defaultCurrency;
  }

  public ArithmeticPolicy arithmeticPolicy() {
    return arithmeticPolicy;
  }

  public FormattingPolicy formattingPolicy() {
    return formattingPolicy;
  }

  public ConversionPolicy conversionPolicy() {
    return conversionPolicy;
  }

  public AllocationStrategy defaultAllocation() {
    return defaultAllocation;
  }

  public TaxConfiguration taxConfiguration() {
    return taxConfiguration;
  }

  public static final class Builder {

    private Currency defaultCurrency;
    private ArithmeticPolicy arithmeticPolicy = ArithmeticPolicies.standard();
    private FormattingPolicy formattingPolicy;
    private ConversionPolicy conversionPolicy;
    private AllocationStrategy defaultAllocation;
    private TaxConfiguration taxConfiguration = TaxConfiguration.disabled();

    public Builder defaultCurrency(Currency currency) {
      this.defaultCurrency = currency;
      return this;
    }

    public Builder arithmeticPolicy(ArithmeticPolicy arithmeticPolicy) {
      this.arithmeticPolicy = arithmeticPolicy;
      return this;
    }

    public Builder formattingPolicy(FormattingPolicy formattingPolicy) {
      this.formattingPolicy = formattingPolicy;
      return this;
    }

    public Builder conversionPolicy(ConversionPolicy conversionPolicy) {
      this.conversionPolicy = conversionPolicy;
      return this;
    }

    public Builder defaultAllocation(AllocationStrategy defaultAllocation) {
      this.defaultAllocation = defaultAllocation;
      return this;
    }

    public Builder taxConfiguration(TaxConfiguration taxConfiguration) {
      this.taxConfiguration = taxConfiguration;
      return this;
    }

    public MoneyConfiguration build() {
      return new MoneyConfiguration(this);
    }
  }
}
