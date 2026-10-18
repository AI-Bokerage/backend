package io.genfin.pricing.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.port.catalog.CatalogRegistry;
import org.junit.jupiter.api.Test;

class CatalogRegistryTest {

  @Test
  void registersAndFindsItemsByCatalogId() {
    CatalogId id = CatalogId.generate();
    CatalogItem item = new CatalogItem(id, new ProductReference("sku-123"));

    CatalogRegistry registry = CatalogRegistries.withProvider(() -> java.util.List.of(item));

    assertThat(registry.find(id)).contains(item);
    assertThat(registry.require(id)).isSameAs(item);
  }

  @Test
  void requireThrowsForUnregisteredItem() {
    CatalogRegistry registry = CatalogRegistries.empty();

    assertThatThrownBy(() -> registry.require(CatalogId.generate()))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }

  @Test
  void catalogFindsItemFromItsSnapshot() {
    CatalogId id = CatalogId.generate();
    CatalogItem item = new CatalogItem(id, new ServiceReference("svc-456"));
    Catalog catalog =
        new Catalog(CatalogVersion.initial(), java.util.List.of(item), CatalogMetadata.empty());

    assertThat(catalog.find(id)).contains(item);
    assertThat(catalog.find(CatalogId.generate())).isEmpty();
  }
}
