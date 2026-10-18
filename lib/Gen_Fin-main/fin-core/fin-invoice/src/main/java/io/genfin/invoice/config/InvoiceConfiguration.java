package io.genfin.invoice.config;

import io.genfin.api.validation.Validate;
import io.genfin.invoice.calculation.InvoiceCalculationPolicy;
import io.genfin.invoice.port.adjustment.AdjustmentEngine;
import io.genfin.invoice.port.discount.DiscountEngine;
import io.genfin.invoice.port.lifecycle.LifecycleProvider;
import io.genfin.invoice.port.numbering.InvoiceNumberGenerator;
import io.genfin.invoice.port.validation.InvoiceValidator;
import io.genfin.money.currency.Currency;
import io.genfin.money.format.FormattingContext;

/** The full, explicit policy set for an Invoice-engine deployment. */
public final class InvoiceConfiguration {

  private final Currency defaultCurrency;
  private final LifecycleProvider lifecycleProvider;
  private final InvoiceValidator validator;
  private final InvoiceCalculationPolicy calculationPolicy;
  private final DiscountEngine discountEngine;
  private final AdjustmentEngine adjustmentEngine;
  private final InvoiceNumberGenerator numberGenerator;
  private final FormattingContext formattingContext;

  private InvoiceConfiguration(Builder builder) {
    this.defaultCurrency =
        Validate.notNull(builder.defaultCurrency, "defaultCurrency must not be null.");
    this.lifecycleProvider =
        Validate.notNull(builder.lifecycleProvider, "lifecycleProvider must not be null.");
    this.validator = Validate.notNull(builder.validator, "validator must not be null.");
    this.calculationPolicy =
        Validate.notNull(builder.calculationPolicy, "calculationPolicy must not be null.");
    this.discountEngine =
        Validate.notNull(builder.discountEngine, "discountEngine must not be null.");
    this.adjustmentEngine =
        Validate.notNull(builder.adjustmentEngine, "adjustmentEngine must not be null.");
    this.numberGenerator =
        Validate.notNull(builder.numberGenerator, "numberGenerator must not be null.");
    this.formattingContext =
        Validate.notNull(builder.formattingContext, "formattingContext must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public Currency defaultCurrency() {
    return defaultCurrency;
  }

  public LifecycleProvider lifecycleProvider() {
    return lifecycleProvider;
  }

  public InvoiceValidator validator() {
    return validator;
  }

  public InvoiceCalculationPolicy calculationPolicy() {
    return calculationPolicy;
  }

  public DiscountEngine discountEngine() {
    return discountEngine;
  }

  public AdjustmentEngine adjustmentEngine() {
    return adjustmentEngine;
  }

  public InvoiceNumberGenerator numberGenerator() {
    return numberGenerator;
  }

  public FormattingContext formattingContext() {
    return formattingContext;
  }

  public static final class Builder {

    private Currency defaultCurrency;
    private LifecycleProvider lifecycleProvider;
    private InvoiceValidator validator;
    private InvoiceCalculationPolicy calculationPolicy;
    private DiscountEngine discountEngine;
    private AdjustmentEngine adjustmentEngine;
    private InvoiceNumberGenerator numberGenerator;
    private FormattingContext formattingContext;

    public Builder defaultCurrency(Currency defaultCurrency) {
      this.defaultCurrency = defaultCurrency;
      return this;
    }

    public Builder lifecycleProvider(LifecycleProvider lifecycleProvider) {
      this.lifecycleProvider = lifecycleProvider;
      return this;
    }

    public Builder validator(InvoiceValidator validator) {
      this.validator = validator;
      return this;
    }

    public Builder calculationPolicy(InvoiceCalculationPolicy calculationPolicy) {
      this.calculationPolicy = calculationPolicy;
      return this;
    }

    public Builder discountEngine(DiscountEngine discountEngine) {
      this.discountEngine = discountEngine;
      return this;
    }

    public Builder adjustmentEngine(AdjustmentEngine adjustmentEngine) {
      this.adjustmentEngine = adjustmentEngine;
      return this;
    }

    public Builder numberGenerator(InvoiceNumberGenerator numberGenerator) {
      this.numberGenerator = numberGenerator;
      return this;
    }

    public Builder formattingContext(FormattingContext formattingContext) {
      this.formattingContext = formattingContext;
      return this;
    }

    public InvoiceConfiguration build() {
      return new InvoiceConfiguration(this);
    }
  }
}
