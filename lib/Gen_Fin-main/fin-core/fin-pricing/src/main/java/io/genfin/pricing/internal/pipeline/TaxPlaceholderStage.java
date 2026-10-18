package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.tax.TaxContextReference;
import io.genfin.pricing.tax.TaxEstimate;
import io.genfin.pricing.tax.TaxPlaceholder;
import java.util.ArrayList;
import java.util.List;

/**
 * The Tax Placeholder stage: asks the configured {@link TaxPlaceholder} to estimate tax for every
 * line's discounted/promoted/coupon/credit-applied {@link Price}, appending whatever {@link
 * TaxEstimate} it returns. With {@link io.genfin.pricing.tax.TaxExtensionPoint#noOp()}
 * (fin-pricing's own default) every estimate is empty, so no component is appended and the price
 * passes through unchanged - exactly as the structural {@code PassThroughStage} it replaces did.
 * Never calculates jurisdiction-specific tax itself; that is entirely the registered {@link
 * TaxPlaceholder}'s concern.
 */
public final class TaxPlaceholderStage implements PricingPipelineStage {

  private final TaxPlaceholder taxPlaceholder;

  public TaxPlaceholderStage(TaxPlaceholder taxPlaceholder) {
    this.taxPlaceholder = Validate.notNull(taxPlaceholder, "taxPlaceholder must not be null.");
  }

  @Override
  public PricingStage stage() {
    return PricingStage.TAX_PLACEHOLDER;
  }

  @Override
  public CalculationResult apply(PricingContext context, CalculationResult result) {
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(result, "result must not be null.");
    List<PricingRequest.Line> lines = context.lines();
    List<Price> prices = result.prices();
    Validate.argument(prices.size() == lines.size(), "prices and lines must be the same size.");
    List<Price> updated = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      PricingRequest.Line line = lines.get(i);
      Price price = prices.get(i);
      TaxContextReference reference =
          TaxContextReference.of(line.catalogId(), context.attributes());
      TaxEstimate estimate = taxPlaceholder.estimate(reference, price.amount());
      updated.add(applyEstimate(price, estimate));
    }
    return result.withPrices(updated);
  }

  private static Price applyEstimate(Price price, TaxEstimate estimate) {
    List<PriceComponent> taxComponents = estimate.breakdown().components();
    if (taxComponents.isEmpty()) {
      return price;
    }
    List<PriceComponent> components = new ArrayList<>(price.breakdown().components());
    components.addAll(taxComponents);
    return new Price(price.catalogId(), new PriceBreakdown(components));
  }
}
