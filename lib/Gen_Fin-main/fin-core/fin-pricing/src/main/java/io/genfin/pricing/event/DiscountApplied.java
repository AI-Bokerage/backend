package io.genfin.pricing.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.DiscountId;
import io.genfin.pricing.id.PricingRequestId;

/** The Discount Engine reduced a pricing request's running total by {@code amount}. */
public record DiscountApplied(
    EventMetadata metadata, PricingRequestId requestId, DiscountId discountId, Money amount)
    implements DomainEvent {}
