package io.genfin.pricing.catalog;

import io.genfin.pricing.internal.catalog.DefaultCatalogRegistry;
import io.genfin.pricing.port.catalog.CatalogItemProvider;
import io.genfin.pricing.port.catalog.CatalogRegistry;

/**
 * Factory for {@link CatalogRegistry} instances. Deliberately has no standard-catalog counterpart -
 * Gen-Fin defines no built-in catalog items, so every registry starts empty until an application
 * supplies its own {@link CatalogItemProvider}. Mirrors {@code
 * io.genfin.ledger.account.AccountTypeRegistries}.
 */
public final class CatalogRegistries {

  private CatalogRegistries() {}

  public static CatalogRegistry empty() {
    return new DefaultCatalogRegistry();
  }

  public static CatalogRegistry withProvider(CatalogItemProvider provider) {
    CatalogRegistry registry = new DefaultCatalogRegistry();
    provider.provide().forEach(registry::register);
    return registry;
  }
}
