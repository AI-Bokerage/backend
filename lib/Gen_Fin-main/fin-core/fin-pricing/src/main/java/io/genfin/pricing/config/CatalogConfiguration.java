package io.genfin.pricing.config;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.catalog.CatalogRegistry;

/** The Catalog Resolution policy set for a fin-pricing deployment. */
public final class CatalogConfiguration {

  private final CatalogRegistry catalogRegistry;

  private CatalogConfiguration(Builder builder) {
    this.catalogRegistry =
        Validate.notNull(builder.catalogRegistry, "catalogRegistry must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public CatalogRegistry catalogRegistry() {
    return catalogRegistry;
  }

  public static final class Builder {

    private CatalogRegistry catalogRegistry;

    public Builder catalogRegistry(CatalogRegistry catalogRegistry) {
      this.catalogRegistry = catalogRegistry;
      return this;
    }

    public CatalogConfiguration build() {
      return new CatalogConfiguration(this);
    }
  }
}
