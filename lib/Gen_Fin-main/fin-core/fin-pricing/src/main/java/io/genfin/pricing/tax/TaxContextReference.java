package io.genfin.pricing.tax;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.pricing.PricingAttributes;

/**
 * A typed pointer back to the Pricing Pipeline state a future Tax Engine needs to estimate tax for
 * one line - the {@link CatalogId} being priced and the run's {@link PricingAttributes} - without
 * fin-pricing itself interpreting any of it. Carries no jurisdiction, registration or tax-category
 * concept: a real Tax Engine reads whatever it needs out of {@code attributes} by its own
 * convention.
 */
public record TaxContextReference(CatalogId catalogId, PricingAttributes attributes)
    implements ValueObject {

  public TaxContextReference {
    Validate.notNull(catalogId, "catalogId must not be null.");
    Validate.notNull(attributes, "attributes must not be null.");
  }

  public static TaxContextReference of(CatalogId catalogId, PricingAttributes attributes) {
    return new TaxContextReference(catalogId, attributes);
  }
}
