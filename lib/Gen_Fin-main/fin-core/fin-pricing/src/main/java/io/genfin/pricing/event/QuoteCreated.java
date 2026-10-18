package io.genfin.pricing.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.id.QuoteId;

/**
 * The Quote Engine turned a priced request into a standing {@link io.genfin.pricing.quote.Quote}.
 */
public record QuoteCreated(EventMetadata metadata, PricingRequestId requestId, QuoteId quoteId)
    implements DomainEvent {}
