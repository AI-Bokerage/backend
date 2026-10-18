package io.genfin.pricing.port.coupon;

import io.genfin.pricing.coupon.Coupon;
import io.genfin.pricing.coupon.CouponCampaign;
import io.genfin.pricing.coupon.CouponCode;
import io.genfin.pricing.coupon.CouponEligibility;
import io.genfin.pricing.coupon.CouponRedemption;
import io.genfin.pricing.coupon.CouponUsage;
import java.util.List;
import java.util.Optional;

/**
 * Registry of the coupon campaigns an application has published for pricing. fin-pricing ships no
 * built-in coupons - an application registers each {@link CouponCode} it issues together with the
 * {@link CouponCampaign} it identifies, the {@link Coupon} arithmetic it contributes and the {@link
 * CouponEligibility} gating it, and the Pricing Pipeline's Coupon Engine stage looks all three up
 * by code. Also the read model for {@link CouponUsage} and the write model for {@link
 * CouponRedemption} - fin-pricing never counts or persists redemptions itself; it only produces the
 * {@link CouponRedemption} fact and leaves recording it to the consuming application. Mirrors
 * {@code io.genfin.pricing.port.catalog.CatalogRegistry}.
 */
public interface CouponRegistry {

  /** Publishes {@code campaign}'s code together with its arithmetic and eligibility gate. */
  void register(CouponCampaign campaign, Coupon coupon, CouponEligibility eligibility);

  Optional<CouponCampaign> findCampaign(CouponCode code);

  Optional<Coupon> findCoupon(CouponCode code);

  Optional<CouponEligibility> findEligibility(CouponCode code);

  /** Current usage of {@code code}, whether or not it is registered. */
  CouponUsage usageOf(CouponCode code);

  /** Records {@code redemption}, counting it against its campaign's {@link CouponUsage}. */
  void recordRedemption(CouponRedemption redemption);

  List<CouponCampaign> findAll();
}
