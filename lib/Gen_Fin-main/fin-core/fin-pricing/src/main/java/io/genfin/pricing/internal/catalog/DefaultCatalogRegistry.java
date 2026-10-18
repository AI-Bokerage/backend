package io.genfin.pricing.internal.catalog;

import io.genfin.pricing.catalog.CatalogItem;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.port.catalog.CatalogRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultCatalogRegistry implements CatalogRegistry {

  private final ConcurrentMap<String, CatalogItem> items = new ConcurrentHashMap<>();

  @Override
  public void register(CatalogItem item) {
    items.put(item.id().value(), item);
  }

  @Override
  public Optional<CatalogItem> find(CatalogId id) {
    return Optional.ofNullable(items.get(id.value()));
  }

  @Override
  public List<CatalogItem> findAll() {
    return List.copyOf(items.values());
  }
}
