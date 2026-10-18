package io.genfin.money.tax;

import io.genfin.api.domain.ValueObject;
import java.util.Map;
import java.util.Optional;

/**
 * An extensible, jurisdiction-agnostic bag of tax-relevant attributes (registration ids, place of
 * supply, ...).
 */
public record TaxMetadata(Map<String, String> attributes) implements ValueObject {

  public TaxMetadata {
    attributes = Map.copyOf(attributes);
  }

  public static TaxMetadata empty() {
    return new TaxMetadata(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(attributes.get(key));
  }
}
