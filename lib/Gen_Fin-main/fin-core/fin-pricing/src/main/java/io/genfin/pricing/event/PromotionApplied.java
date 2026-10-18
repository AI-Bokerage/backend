package io.genfin.pricing.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.id.PromotionId;

/** The Promotion Engine reduced a pricing request's running total by {@code amount}. */
public record PromotionApplied(
    EventMetadata metadata, PricingRequestId requestId, PromotionId promotionId, Money amount)
    implements DomainEvent {}
