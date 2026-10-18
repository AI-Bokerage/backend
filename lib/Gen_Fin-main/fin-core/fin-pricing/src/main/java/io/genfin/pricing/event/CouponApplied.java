package io.genfin.pricing.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.CouponId;
import io.genfin.pricing.id.PricingRequestId;

/** The Coupon Engine reduced a pricing request's running total by {@code amount}. */
public record CouponApplied(
    EventMetadata metadata, PricingRequestId requestId, CouponId couponId, Money amount)
    implements DomainEvent {}
