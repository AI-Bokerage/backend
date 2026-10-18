package io.genfin.pricing.catalog;

import io.genfin.api.domain.ValueObject;

/**
 * A {@link CatalogItem}'s pointer into the application's own domain - a {@link ProductReference} or
 * a {@link ServiceReference}. fin-pricing never owns or persists what that value identifies; it
 * only carries it through the Pricing Pipeline so downstream modules (Invoice, Payment, Ledger) can
 * resolve it back to the application's real product or service record.
 */
public sealed interface PricingReference extends ValueObject
    permits ProductReference, ServiceReference {

  String value();
}
