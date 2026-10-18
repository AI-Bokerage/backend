package io.genfin.pricing.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.pricing.id.PricingRequestId;

/**
 * A {@link io.genfin.pricing.pricing.PricingRequest} was rejected by validation or a commercial
 * rule.
 */
public record PricingRejected(EventMetadata metadata, PricingRequestId requestId, String reason)
    implements DomainEvent {}
