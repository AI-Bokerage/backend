package io.genfin.pricing.pricing;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.PricingRequestId;
import java.util.List;
import java.util.Optional;

/**
 * The running, immutable state threaded through the Pricing Pipeline's stages (Catalog Resolution,
 * Base Price Resolution, Discount/Promotion/Coupon/Credit Engines, Tax Placeholder, Rounding,
 * Validation). Each {@code io.genfin.pricing.pipeline} stage receives one context and returns a new
 * one carrying its contribution - nothing here is mutated in place.
 */
public record PricingContext(
    PricingRequestId requestId,
    List<PricingRequest.Line> lines,
    PricingAttributes attributes,
    Optional<Money> baseAmount,
    Optional<Money> reductionAmount)
    implements ValueObject {

  public PricingContext {
    Validate.notNull(requestId, "requestId must not be null.");
    lines = List.copyOf(lines);
    Validate.notNull(attributes, "attributes must not be null.");
    Validate.notNull(baseAmount, "baseAmount must not be null.");
    Validate.notNull(reductionAmount, "reductionAmount must not be null.");
  }

  public static PricingContext start(
      PricingRequestId requestId, List<PricingRequest.Line> lines, PricingAttributes attributes) {
    return new PricingContext(requestId, lines, attributes, Optional.empty(), Optional.empty());
  }

  public PricingContext withBaseAmount(Money baseAmount) {
    return new PricingContext(
        requestId, lines, attributes, Optional.of(baseAmount), reductionAmount);
  }

  public PricingContext withReductionAmount(Money reductionAmount) {
    return new PricingContext(
        requestId, lines, attributes, baseAmount, Optional.of(reductionAmount));
  }
}
