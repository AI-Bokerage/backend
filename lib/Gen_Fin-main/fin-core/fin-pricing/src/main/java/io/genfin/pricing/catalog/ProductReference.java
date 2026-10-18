package io.genfin.pricing.catalog;

import io.genfin.api.validation.Validate;

/** A {@link CatalogItem}'s pointer to an application-owned product record. */
public record ProductReference(String value) implements PricingReference {

  public ProductReference {
    Validate.notBlank(value, "value must not be blank.");
  }
}
