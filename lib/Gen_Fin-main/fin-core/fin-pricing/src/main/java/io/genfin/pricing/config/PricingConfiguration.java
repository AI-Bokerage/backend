package io.genfin.pricing.config;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.calculation.PricingPolicy;
import io.genfin.pricing.port.lifecycle.PricingLifecycleProvider;
import io.genfin.pricing.port.validation.PricingValidator;
import io.genfin.pricing.tax.TaxPlaceholder;

/**
 * The full, explicit policy set for a fin-pricing deployment: the cross-cutting policies (Base
 * Price Resolution, lifecycle, Tax Placeholder, Pricing Validation) plus the composed
 * sub-configurations for the concerns that carry more than one collaborator each (Catalog,
 * Discount, Promotion, Coupon, Credit, Quote, Commercial Rule). Mirrors {@code
 * io.genfin.ledger.config.LedgerConfiguration}.
 */
public final class PricingConfiguration {

  private final PricingPolicy pricingPolicy;
  private final PricingLifecycleProvider lifecycleProvider;
  private final TaxPlaceholder taxPlaceholder;
  private final PricingValidator pricingValidator;
  private final CatalogConfiguration catalogConfiguration;
  private final DiscountConfiguration discountConfiguration;
  private final PromotionConfiguration promotionConfiguration;
  private final CouponConfiguration couponConfiguration;
  private final CreditConfiguration creditConfiguration;
  private final QuoteConfiguration quoteConfiguration;
  private final CommercialRuleConfiguration commercialRuleConfiguration;

  private PricingConfiguration(Builder builder) {
    this.pricingPolicy = Validate.notNull(builder.pricingPolicy, "pricingPolicy must not be null.");
    this.lifecycleProvider =
        Validate.notNull(builder.lifecycleProvider, "lifecycleProvider must not be null.");
    this.taxPlaceholder =
        Validate.notNull(builder.taxPlaceholder, "taxPlaceholder must not be null.");
    this.pricingValidator =
        Validate.notNull(builder.pricingValidator, "pricingValidator must not be null.");
    this.catalogConfiguration =
        Validate.notNull(builder.catalogConfiguration, "catalogConfiguration must not be null.");
    this.discountConfiguration =
        Validate.notNull(builder.discountConfiguration, "discountConfiguration must not be null.");
    this.promotionConfiguration =
        Validate.notNull(
            builder.promotionConfiguration, "promotionConfiguration must not be null.");
    this.couponConfiguration =
        Validate.notNull(builder.couponConfiguration, "couponConfiguration must not be null.");
    this.creditConfiguration =
        Validate.notNull(builder.creditConfiguration, "creditConfiguration must not be null.");
    this.quoteConfiguration =
        Validate.notNull(builder.quoteConfiguration, "quoteConfiguration must not be null.");
    this.commercialRuleConfiguration =
        Validate.notNull(
            builder.commercialRuleConfiguration, "commercialRuleConfiguration must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public PricingPolicy pricingPolicy() {
    return pricingPolicy;
  }

  public PricingLifecycleProvider lifecycleProvider() {
    return lifecycleProvider;
  }

  public TaxPlaceholder taxPlaceholder() {
    return taxPlaceholder;
  }

  public PricingValidator pricingValidator() {
    return pricingValidator;
  }

  public CatalogConfiguration catalogConfiguration() {
    return catalogConfiguration;
  }

  public DiscountConfiguration discountConfiguration() {
    return discountConfiguration;
  }

  public PromotionConfiguration promotionConfiguration() {
    return promotionConfiguration;
  }

  public CouponConfiguration couponConfiguration() {
    return couponConfiguration;
  }

  public CreditConfiguration creditConfiguration() {
    return creditConfiguration;
  }

  public QuoteConfiguration quoteConfiguration() {
    return quoteConfiguration;
  }

  public CommercialRuleConfiguration commercialRuleConfiguration() {
    return commercialRuleConfiguration;
  }

  public static final class Builder {

    private PricingPolicy pricingPolicy;
    private PricingLifecycleProvider lifecycleProvider;
    private TaxPlaceholder taxPlaceholder;
    private PricingValidator pricingValidator;
    private CatalogConfiguration catalogConfiguration;
    private DiscountConfiguration discountConfiguration;
    private PromotionConfiguration promotionConfiguration;
    private CouponConfiguration couponConfiguration;
    private CreditConfiguration creditConfiguration;
    private QuoteConfiguration quoteConfiguration;
    private CommercialRuleConfiguration commercialRuleConfiguration;

    public Builder pricingPolicy(PricingPolicy pricingPolicy) {
      this.pricingPolicy = pricingPolicy;
      return this;
    }

    public Builder lifecycleProvider(PricingLifecycleProvider lifecycleProvider) {
      this.lifecycleProvider = lifecycleProvider;
      return this;
    }

    public Builder taxPlaceholder(TaxPlaceholder taxPlaceholder) {
      this.taxPlaceholder = taxPlaceholder;
      return this;
    }

    public Builder pricingValidator(PricingValidator pricingValidator) {
      this.pricingValidator = pricingValidator;
      return this;
    }

    public Builder catalogConfiguration(CatalogConfiguration catalogConfiguration) {
      this.catalogConfiguration = catalogConfiguration;
      return this;
    }

    public Builder discountConfiguration(DiscountConfiguration discountConfiguration) {
      this.discountConfiguration = discountConfiguration;
      return this;
    }

    public Builder promotionConfiguration(PromotionConfiguration promotionConfiguration) {
      this.promotionConfiguration = promotionConfiguration;
      return this;
    }

    public Builder couponConfiguration(CouponConfiguration couponConfiguration) {
      this.couponConfiguration = couponConfiguration;
      return this;
    }

    public Builder creditConfiguration(CreditConfiguration creditConfiguration) {
      this.creditConfiguration = creditConfiguration;
      return this;
    }

    public Builder quoteConfiguration(QuoteConfiguration quoteConfiguration) {
      this.quoteConfiguration = quoteConfiguration;
      return this;
    }

    public Builder commercialRuleConfiguration(
        CommercialRuleConfiguration commercialRuleConfiguration) {
      this.commercialRuleConfiguration = commercialRuleConfiguration;
      return this;
    }

    public PricingConfiguration build() {
      return new PricingConfiguration(this);
    }
  }
}
