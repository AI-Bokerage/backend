package io.genfin.pricing.catalog;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * An optimistic-concurrency version marker for a {@link Catalog}: bumped each time an application
 * republishes its catalog, so a {@link CatalogItem} resolved during pricing can be traced back to
 * the catalog snapshot it came from. Mirrors {@code io.genfin.pricing.pricing.PricingVersion}.
 */
public record CatalogVersion(int number) implements ValueObject {

  public CatalogVersion {
    Validate.nonNegative(number, "number must not be negative.");
  }

  public static CatalogVersion initial() {
    return new CatalogVersion(1);
  }

  public CatalogVersion next() {
    return new CatalogVersion(number + 1);
  }
}
