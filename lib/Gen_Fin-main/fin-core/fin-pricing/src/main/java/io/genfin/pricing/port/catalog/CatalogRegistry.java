package io.genfin.pricing.port.catalog;

import io.genfin.api.exception.ValidationException;
import io.genfin.pricing.catalog.CatalogItem;
import io.genfin.pricing.id.CatalogId;
import java.util.List;
import java.util.Optional;

/**
 * Registry of the {@link CatalogItem}s an application has published for pricing. fin-pricing ships
 * no built-in catalog items - an application registers its own products/services here (typically
 * via a {@link CatalogItemProvider}) and the Pricing Pipeline's Catalog Resolution stage looks them
 * up by {@link CatalogId}. Mirrors {@code io.genfin.ledger.port.account.AccountTypeRegistry}.
 */
public interface CatalogRegistry {

  void register(CatalogItem item);

  Optional<CatalogItem> find(CatalogId id);

  default CatalogItem require(CatalogId id) {
    return find(id)
        .orElseThrow(() -> new ValidationException("Unregistered catalog item: " + id.value()));
  }

  List<CatalogItem> findAll();
}
