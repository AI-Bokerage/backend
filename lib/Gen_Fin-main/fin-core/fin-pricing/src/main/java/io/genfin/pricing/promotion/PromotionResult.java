package io.genfin.pricing.promotion;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.price.Price;
import java.util.Optional;

/**
 * The Promotion Engine's outcome for one line: the resulting {@link Price} (unchanged if no
 * campaign was eligible) and, when one was, which {@link PromotionCampaign} won - unlike {@code
 * io.genfin.pricing.discount.Discount}, which stacks silently, a promotion is customer-facing
 * marketing, so callers (receipts, analytics) need to know which single campaign was credited.
 */
public record PromotionResult(Price price, Optional<PromotionCampaign> appliedCampaign)
    implements ValueObject {

  public PromotionResult {
    Validate.notNull(price, "price must not be null.");
    Validate.notNull(appliedCampaign, "appliedCampaign must not be null.");
  }

  /** No eligible campaign - the line's price is unchanged. */
  public static PromotionResult unchanged(Price price) {
    return new PromotionResult(price, Optional.empty());
  }

  /** {@code campaign} was applied, producing {@code price}. */
  public static PromotionResult applied(Price price, PromotionCampaign campaign) {
    Validate.notNull(campaign, "campaign must not be null.");
    return new PromotionResult(price, Optional.of(campaign));
  }

  public boolean hasPromotion() {
    return appliedCampaign.isPresent();
  }
}
