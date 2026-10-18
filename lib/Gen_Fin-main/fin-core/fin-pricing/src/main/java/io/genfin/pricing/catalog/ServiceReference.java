package io.genfin.pricing.catalog;

import io.genfin.api.validation.Validate;

/** A {@link CatalogItem}'s pointer to an application-owned service record. */
public record ServiceReference(String value) implements PricingReference {

  public ServiceReference {
    Validate.notBlank(value, "value must not be blank.");
  }
}
