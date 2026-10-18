package io.genfin.pricing.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.pricing.calculation.PricingEngines;
import io.genfin.pricing.calculation.PricingPolicies;
import io.genfin.pricing.catalog.CatalogItem;
import io.genfin.pricing.catalog.CatalogRegistries;
import io.genfin.pricing.catalog.ProductReference;
import io.genfin.pricing.coupon.CouponPolicies;
import io.genfin.pricing.coupon.CouponRegistries;
import io.genfin.pricing.credit.CreditPolicies;
import io.genfin.pricing.credit.CreditRegistries;
import io.genfin.pricing.discount.DiscountPolicies;
import io.genfin.pricing.discount.DiscountRule;
import io.genfin.pricing.discount.DiscountStrategies;
import io.genfin.pricing.discount.PercentageDiscount;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.port.calculation.PricingEngine;
import io.genfin.pricing.port.calculation.PricingPolicy;
import io.genfin.pricing.port.calculation.PricingStrategy;
import io.genfin.pricing.port.catalog.CatalogRegistry;
import io.genfin.pricing.port.discount.DiscountPolicy;
import io.genfin.pricing.port.pipeline.PricingPipeline;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.pricing.PricingResult;
import io.genfin.pricing.pricing.PricingVersion;
import io.genfin.pricing.promotion.PromotionPolicies;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End-to-end exercise of the Pricing Pipeline shape: an application-registered {@link
 * PricingStrategy} resolves a flat unit price, the remaining stages are the structural
 * pass-through/validation placeholders, and {@link PricingEngine} collapses the run into a {@link
 * PricingResult}.
 */
class PricingPipelineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void runsTheFullPipelineShapeEndToEnd() {
    CatalogId catalogId = CatalogId.generate();
    CatalogItem item = new CatalogItem(catalogId, new ProductReference("sku-1"));
    CatalogRegistry catalogRegistry = CatalogRegistries.withProvider(() -> List.of(item));

    PricingStrategy flatUnitPrice =
        new PricingStrategy() {
          @Override
          public boolean supports(PricingRequest.Line line, PricingContext context) {
            return line.catalogId().equals(catalogId);
          }

          @Override
          public Price resolve(PricingRequest.Line line, PricingContext context) {
            Money unitAmount =
                Money.of(20, USD).multiply(java.math.BigDecimal.valueOf(line.quantity()));
            return new Price(
                line.catalogId(),
                PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", unitAmount)));
          }
        };
    PricingPolicy policy = PricingPolicies.of(List.of(flatUnitPrice));
    PricingPipeline pipeline = PricingPipelines.standard(policy, catalogRegistry, List.of());
    PricingEngine engine = PricingEngines.of(pipeline);

    PricingRequestId requestId = PricingRequestId.generate();
    PricingRequest request =
        new PricingRequest(
            requestId,
            List.of(new PricingRequest.Line(catalogId, 3)),
            Instant.parse("2026-01-01T00:00:00Z"));

    PricingResult result =
        engine.calculate(request, PricingVersion.initial(), Instant.parse("2026-01-01T00:00:01Z"));

    assertThat(result.requestId()).isEqualTo(requestId);
    assertThat(result.summary().baseAmount()).isEqualTo(Money.of(60, USD));
    assertThat(result.summary().netAmount()).isEqualTo(Money.of(60, USD));
  }

  @Test
  void runsCatalogThroughDiscountThroughValidationEndToEnd() {
    // Exercises Catalog Resolution -> Base Price Resolution -> Discount Engine -> Promotion
    // Engine (empty) -> Coupon Engine (empty) -> Credit Engine (empty) -> Tax Placeholder
    // (no-op) -> Rounding (pass-through) -> Pricing Validation -> PricingResult, with the
    // Discount Engine actively reducing the price so the chained reduction is observable in the
    // final summary rather than only in the Discount Engine's own unit tests.
    CatalogId catalogId = CatalogId.generate();
    CatalogItem item = new CatalogItem(catalogId, new ProductReference("sku-2"));
    CatalogRegistry catalogRegistry = CatalogRegistries.withProvider(() -> List.of(item));

    PricingStrategy flatUnitPrice =
        new PricingStrategy() {
          @Override
          public boolean supports(PricingRequest.Line line, PricingContext context) {
            return line.catalogId().equals(catalogId);
          }

          @Override
          public Price resolve(PricingRequest.Line line, PricingContext context) {
            return new Price(
                line.catalogId(),
                PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(100, USD))));
          }
        };
    PricingPolicy policy = PricingPolicies.of(List.of(flatUnitPrice));

    DiscountRule tenPercentOff =
        DiscountRule.of(
            "STANDARD_DISCOUNT",
            PercentageDiscount.of("10% off", Percentage.ofPercent(BigDecimal.TEN)),
            (line, context) -> true);
    DiscountPolicy discountPolicy =
        DiscountPolicies.of(List.of(DiscountStrategies.fromRules(List.of(tenPercentOff))));

    PricingPipeline pipeline =
        PricingPipelines.standard(
            policy,
            discountPolicy,
            PromotionPolicies.empty(),
            CouponPolicies.empty(),
            CouponRegistries.empty(),
            CreditPolicies.empty(),
            CreditRegistries.empty(),
            catalogRegistry,
            List.of());
    PricingEngine engine = PricingEngines.of(pipeline);

    PricingRequestId requestId = PricingRequestId.generate();
    PricingRequest request =
        new PricingRequest(
            requestId,
            List.of(new PricingRequest.Line(catalogId, 1)),
            Instant.parse("2026-01-01T00:00:00Z"));

    PricingResult result =
        engine.calculate(request, PricingVersion.initial(), Instant.parse("2026-01-01T00:00:01Z"));

    assertThat(result.summary().baseAmount()).isEqualTo(Money.of(100, USD));
    assertThat(result.summary().reductionAmount()).isEqualTo(Money.of(-10, USD));
    assertThat(result.summary().netAmount()).isEqualTo(Money.of(90, USD));
  }

  @Test
  void rejectsAnUnregisteredCatalogItem() {
    CatalogRegistry catalogRegistry = CatalogRegistries.empty();
    PricingPipeline pipeline =
        PricingPipelines.standard(PricingPolicies.empty(), catalogRegistry, List.of());

    PricingContext context =
        PricingContext.start(
            PricingRequestId.generate(),
            List.of(new PricingRequest.Line(CatalogId.generate(), 1)),
            io.genfin.pricing.pricing.PricingAttributes.empty());

    assertThatThrownBy(() -> pipeline.run(context))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }
}
