package io.genfin.pricing.catalog;

import io.genfin.api.domain.ValueObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Free-form system metadata carried on a {@link Catalog} or {@link CatalogItem}. Mirrors {@code
 * io.genfin.pricing.pricing.PricingMetadata}.
 */
public record CatalogMetadata(Map<String, String> values) implements ValueObject {

  public CatalogMetadata {
    values = Map.copyOf(values);
  }

  public static CatalogMetadata empty() {
    return new CatalogMetadata(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }

  public CatalogMetadata with(String key, String value) {
    Map<String, String> merged = new LinkedHashMap<>(values);
    merged.put(key, value);
    return new CatalogMetadata(merged);
  }
}
