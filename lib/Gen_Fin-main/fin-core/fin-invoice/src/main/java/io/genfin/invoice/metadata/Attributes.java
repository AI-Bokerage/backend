package io.genfin.invoice.metadata;

import io.genfin.api.domain.ValueObject;
import java.util.Map;
import java.util.Optional;

/** A flat, string-only tag bag — lighter weight than {@link Metadata} for simple labels. */
public record Attributes(Map<String, String> values) implements ValueObject {

  public Attributes {
    values = Map.copyOf(values);
  }

  public static Attributes empty() {
    return new Attributes(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }
}
