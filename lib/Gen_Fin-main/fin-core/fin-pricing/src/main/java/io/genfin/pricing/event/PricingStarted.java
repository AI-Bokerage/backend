package io.genfin.pricing.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.pricing.id.PricingRequestId;

/** A {@link io.genfin.pricing.pricing.PricingRequest} entered the Pricing Pipeline. */
public record PricingStarted(EventMetadata metadata, PricingRequestId requestId)
    implements DomainEvent {}
