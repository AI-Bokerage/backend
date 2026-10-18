package io.genfin.pricing.coupon;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.CouponId;
import java.time.Instant;
import java.util.Optional;

/**
 * The named coupon campaign a presented {@link CouponCode} redeems - identity, the code itself, a
 * human-readable name/description, an optional active window ({@code startsAt}/{@code endsAt}) and
 * an optional redemption limit ({@code maxRedemptions}) a {@link
 * io.genfin.pricing.port.coupon.CouponRegistry} tracks via {@link CouponUsage}. Carries no
 * arithmetic of its own - that is {@link Coupon}'s concern - and no eligibility of its own - that
 * is {@link CouponEligibility}'s concern - so the same campaign metadata can back campaigns with
 * different reduction/eligibility combinations. Mirrors {@code
 * io.genfin.pricing.promotion.PromotionCampaign}.
 */
public record CouponCampaign(
    CouponId id,
    CouponCode code,
    String name,
    String description,
    Optional<Instant> startsAt,
    Optional<Instant> endsAt,
    Optional<Integer> maxRedemptions)
    implements ValueObject {

  public CouponCampaign {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(code, "code must not be null.");
    Validate.notBlank(name, "name must not be blank.");
    Validate.notBlank(description, "description must not be blank.");
    Validate.notNull(startsAt, "startsAt must not be null.");
    Validate.notNull(endsAt, "endsAt must not be null.");
    Validate.notNull(maxRedemptions, "maxRedemptions must not be null.");
    maxRedemptions.ifPresent(limit -> Validate.positive(limit, "maxRedemptions must be positive."));
  }

  /** A campaign with no active window and no redemption limit. */
  public static CouponCampaign of(CouponCode code, String name, String description) {
    return new CouponCampaign(
        CouponId.generate(),
        code,
        name,
        description,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /** A campaign active only within {@code [startsAt, endsAt]}, with no redemption limit. */
  public static CouponCampaign windowed(
      CouponCode code, String name, String description, Instant startsAt, Instant endsAt) {
    Validate.notNull(startsAt, "startsAt must not be null.");
    Validate.notNull(endsAt, "endsAt must not be null.");
    return new CouponCampaign(
        CouponId.generate(),
        code,
        name,
        description,
        Optional.of(startsAt),
        Optional.of(endsAt),
        Optional.empty());
  }

  /** This campaign, capped to at most {@code limit} total redemptions. */
  public CouponCampaign withMaxRedemptions(int limit) {
    Validate.positive(limit, "limit must be positive.");
    return new CouponCampaign(id, code, name, description, startsAt, endsAt, Optional.of(limit));
  }

  /** Whether {@code instant} falls within this campaign's active window, if it has one. */
  public boolean isActiveAt(Instant instant) {
    Validate.notNull(instant, "instant must not be null.");
    return startsAt.map(start -> !instant.isBefore(start)).orElse(true)
        && endsAt.map(end -> !instant.isAfter(end)).orElse(true);
  }
}
