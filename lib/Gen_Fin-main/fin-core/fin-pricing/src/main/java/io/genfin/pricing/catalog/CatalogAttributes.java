package io.genfin.pricing.catalog;

import io.genfin.api.domain.ValueObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Application-supplied input attributes on a {@link CatalogItem} (e.g. plan tier, unit of measure)
 * that pricing pipeline stages read. Mirrors {@code io.genfin.pricing.pricing.PricingAttributes}.
 */
public record CatalogAttributes(Map<String, String> values) implements ValueObject {

  public CatalogAttributes {
    values = Map.copyOf(values);
  }

  public static CatalogAttributes empty() {
    return new CatalogAttributes(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }

  public CatalogAttributes with(String key, String value) {
    Map<String, String> merged = new LinkedHashMap<>(values);
    merged.put(key, value);
    return new CatalogAttributes(merged);
  }
}
