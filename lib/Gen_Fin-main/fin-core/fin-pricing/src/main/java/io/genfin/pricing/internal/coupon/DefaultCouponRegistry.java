package io.genfin.pricing.internal.coupon;

import io.genfin.pricing.coupon.Coupon;
import io.genfin.pricing.coupon.CouponCampaign;
import io.genfin.pricing.coupon.CouponCode;
import io.genfin.pricing.coupon.CouponEligibility;
import io.genfin.pricing.coupon.CouponRedemption;
import io.genfin.pricing.coupon.CouponUsage;
import io.genfin.pricing.port.coupon.CouponRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultCouponRegistry implements CouponRegistry {

  private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicInteger> usage = new ConcurrentHashMap<>();

  @Override
  public void register(CouponCampaign campaign, Coupon coupon, CouponEligibility eligibility) {
    entries.put(campaign.code().value(), new Entry(campaign, coupon, eligibility));
  }

  @Override
  public Optional<CouponCampaign> findCampaign(CouponCode code) {
    return entry(code).map(Entry::campaign);
  }

  @Override
  public Optional<Coupon> findCoupon(CouponCode code) {
    return entry(code).map(Entry::coupon);
  }

  @Override
  public Optional<CouponEligibility> findEligibility(CouponCode code) {
    return entry(code).map(Entry::eligibility);
  }

  @Override
  public CouponUsage usageOf(CouponCode code) {
    int redemptions = usage.getOrDefault(code.value(), new AtomicInteger()).get();
    return CouponUsage.of(
        code, redemptions, findCampaign(code).flatMap(CouponCampaign::maxRedemptions));
  }

  @Override
  public void recordRedemption(CouponRedemption redemption) {
    usage.computeIfAbsent(redemption.code().value(), key -> new AtomicInteger()).incrementAndGet();
  }

  @Override
  public List<CouponCampaign> findAll() {
    return entries.values().stream().map(Entry::campaign).toList();
  }

  private Optional<Entry> entry(CouponCode code) {
    return Optional.ofNullable(entries.get(code.value()));
  }

  private record Entry(CouponCampaign campaign, Coupon coupon, CouponEligibility eligibility) {}
}
