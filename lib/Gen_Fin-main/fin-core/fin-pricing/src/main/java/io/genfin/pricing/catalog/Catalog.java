package io.genfin.pricing.catalog;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.CatalogId;
import java.util.List;
import java.util.Optional;

/**
 * An immutable, versioned snapshot of the {@link CatalogItem}s an application has published for
 * pricing. fin-pricing never populates a {@code Catalog} itself - an application builds one from
 * its own product/service data and the Catalog Resolution pipeline stage reads from it (or from a
 * {@link io.genfin.pricing.port.catalog.CatalogRegistry} instead, for lookups that shouldn't
 * require rebuilding a full snapshot).
 */
public record Catalog(CatalogVersion version, List<CatalogItem> items, CatalogMetadata metadata)
    implements ValueObject {

  public Catalog {
    Validate.notNull(version, "version must not be null.");
    items = List.copyOf(items);
    Validate.notNull(metadata, "metadata must not be null.");
  }

  public static Catalog empty() {
    return new Catalog(CatalogVersion.initial(), List.of(), CatalogMetadata.empty());
  }

  public Optional<CatalogItem> find(CatalogId id) {
    return items.stream().filter(item -> item.id().equals(id)).findFirst();
  }
}
