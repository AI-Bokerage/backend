package io.genfin.pricing.discount;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.pricing.id.DiscountId;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.price.PriceModifier;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * A {@link Discount} whose {@link Percentage} rate depends on the ordered {@link
 * PricingRequest.Line#quantity()} - e.g. 5% at 10+ units, 10% at 50+ units. The highest {@link
 * Tier} whose {@link Tier#minQuantity()} the line's quantity meets or exceeds wins; below every
 * tier's threshold, no reduction applies.
 */
public record VolumeDiscount(DiscountId id, String description, List<Tier> tiers)
    implements Discount {

  public VolumeDiscount {
    Validate.notNull(id, "id must not be null.");
    Validate.notBlank(description, "description must not be blank.");
    Validate.notNull(tiers, "tiers must not be null.");
    Validate.argument(!tiers.isEmpty(), "tiers must not be empty.");
    tiers = List.copyOf(tiers);
  }

  public static VolumeDiscount of(String description, List<Tier> tiers) {
    return new VolumeDiscount(DiscountId.generate(), description, tiers);
  }

  @Override
  public DiscountType type() {
    return DiscountType.VOLUME;
  }

  @Override
  public PriceAdjustment applyTo(
      Money runningAmount, PricingRequest.Line line, PricingContext context) {
    Validate.notNull(runningAmount, "runningAmount must not be null.");
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    Percentage rate =
        tiers.stream()
            .filter(tier -> line.quantity() >= tier.minQuantity())
            .max(Comparator.comparingInt(Tier::minQuantity))
            .map(Tier::percentage)
            .orElseGet(() -> Percentage.ofFraction(BigDecimal.ZERO));
    return PriceAdjustment.of(
        PriceType.DISCOUNT, PriceModifier.percentage(rate.negate()), runningAmount, description);
  }

  /** One quantity threshold and the {@link Percentage} rate that applies once it is reached. */
  public record Tier(int minQuantity, Percentage percentage) implements ValueObject {

    public Tier {
      Validate.argument(minQuantity >= 0, "minQuantity must not be negative.");
      Validate.notNull(percentage, "percentage must not be null.");
    }
  }
}
