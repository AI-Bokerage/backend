package io.genfin.pricing.coupon;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.CouponId;
import io.genfin.pricing.id.PricingRequestId;
import java.time.Instant;

/**
 * Evidence that a {@link CouponCampaign} was applied to one {@link
 * io.genfin.pricing.pricing.PricingRequest} - fin-pricing produces this fact as part of a {@link
 * CouponResult} but never commits it: pricing calculates commercial value before invoices or
 * payments exist, so whether a redemption is actually counted against {@link CouponUsage} (by
 * calling {@code CouponRegistry#recordRedemption}) is left to the consuming application, typically
 * only once the priced request is turned into a real order.
 */
public record CouponRedemption(
    CouponId campaignId, CouponCode code, PricingRequestId requestId, Instant redeemedAt)
    implements ValueObject {

  public CouponRedemption {
    Validate.notNull(campaignId, "campaignId must not be null.");
    Validate.notNull(code, "code must not be null.");
    Validate.notNull(requestId, "requestId must not be null.");
    Validate.notNull(redeemedAt, "redeemedAt must not be null.");
  }

  /** A redemption of {@code campaign} for {@code requestId}, timestamped now. */
  public static CouponRedemption of(CouponCampaign campaign, PricingRequestId requestId) {
    Validate.notNull(campaign, "campaign must not be null.");
    return new CouponRedemption(campaign.id(), campaign.code(), requestId, Instant.now());
  }
}
