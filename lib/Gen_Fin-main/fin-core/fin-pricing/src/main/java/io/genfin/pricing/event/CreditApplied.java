package io.genfin.pricing.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.CreditId;
import io.genfin.pricing.id.PricingRequestId;

/** The Credit Engine reduced a pricing request's running total by {@code amount}. */
public record CreditApplied(
    EventMetadata metadata, PricingRequestId requestId, CreditId creditId, Money amount)
    implements DomainEvent {}
