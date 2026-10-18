package io.genfin.pricing.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.port.validation.PricingValidator;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingAttributes;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Validators#standard()}'s composed default rules and the collect-everything
 * {@link ValidationResult} shape they feed.
 */
class PricingValidationTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  private static PricingContext context() {
    return PricingContext.start(
        PricingRequestId.generate(),
        List.of(new PricingRequest.Line(CatalogId.generate(), 1)),
        PricingAttributes.empty());
  }

  @Test
  void validResultProducesNoIssues() {
    PricingValidator validator = Validators.standard();
    Price price =
        new Price(
            CatalogId.generate(),
            PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(100, USD))));
    CalculationResult result = CalculationResult.empty().withPrices(List.of(price));

    ValidationResult validation = validator.validate(result, ValidationContext.of(context()));

    assertThat(validation.isValid()).isTrue();
    assertThat(validation.issues()).isEmpty();
  }

  @Test
  void negativeNetPriceIsFlaggedAsCritical() {
    PricingValidator validator = Validators.standard();
    Price negative =
        new Price(
            CatalogId.generate(),
            PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(-5, USD))));
    CalculationResult result = CalculationResult.empty().withPrices(List.of(negative));

    ValidationResult validation = validator.validate(result, ValidationContext.of(context()));

    assertThat(validation.isValid()).isFalse();
    assertThat(validation.issues())
        .anyMatch(issue -> "NEGATIVE_NET_PRICE".equals(issue.ruleCode()));
  }

  @Test
  void discountExceedingBasePriceIsFlagged() {
    PricingValidator validator = Validators.standard();
    Price overDiscounted =
        new Price(
            CatalogId.generate(),
            PriceBreakdown.of(
                PriceComponent.of(PriceType.BASE, "base", Money.of(50, USD)),
                PriceComponent.of(PriceType.DISCOUNT, "over-discount", Money.of(-60, USD))));
    CalculationResult result = CalculationResult.empty().withPrices(List.of(overDiscounted));

    ValidationResult validation = validator.validate(result, ValidationContext.of(context()));

    assertThat(validation.issues())
        .anyMatch(issue -> "DISCOUNT_EXCEEDS_BASE_PRICE".equals(issue.ruleCode()));
  }

  @Test
  void couponThatIncreasesThePriceIsFlagged() {
    PricingValidator validator = Validators.standard();
    Price badCoupon =
        new Price(
            CatalogId.generate(),
            PriceBreakdown.of(
                PriceComponent.of(PriceType.BASE, "base", Money.of(50, USD)),
                PriceComponent.of(PriceType.COUPON, "bad-coupon", Money.of(5, USD))));
    CalculationResult result = CalculationResult.empty().withPrices(List.of(badCoupon));

    ValidationResult validation = validator.validate(result, ValidationContext.of(context()));

    assertThat(validation.issues())
        .anyMatch(issue -> "COUPON_INCREASES_PRICE".equals(issue.ruleCode()));
  }

  @Test
  void configuredMinimumPriceIsOnlyCheckedWhenSupplied() {
    PricingValidator validator = Validators.standard();
    Price belowMinimum =
        new Price(
            CatalogId.generate(),
            PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(10, USD))));
    CalculationResult result = CalculationResult.empty().withPrices(List.of(belowMinimum));

    ValidationResult withoutMinimum = validator.validate(result, ValidationContext.of(context()));
    assertThat(withoutMinimum.isValid()).isTrue();

    ValidationResult withMinimum =
        validator.validate(result, ValidationContext.of(context()).withMinPrice(Money.of(20, USD)));
    assertThat(withMinimum.issues())
        .anyMatch(issue -> "PRICE_BELOW_CONFIGURED_MINIMUM".equals(issue.ruleCode()));
  }

  @Test
  void validationResultValidWhenEmpty() {
    assertThat(ValidationResult.valid().isValid()).isTrue();
    assertThat(ValidationResult.valid().issues()).isEmpty();
  }
}
