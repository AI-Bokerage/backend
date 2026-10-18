package io.genfin.pricing.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.price.Price;

/** Base price resolution produced a {@link Price} for one catalog line of a pricing request. */
public record PriceCalculated(
    EventMetadata metadata, PricingRequestId requestId, CatalogId catalogId, Price price)
    implements DomainEvent {}
