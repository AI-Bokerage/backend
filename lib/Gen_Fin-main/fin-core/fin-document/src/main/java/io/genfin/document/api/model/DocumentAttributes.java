package io.genfin.document.api.model;

import java.util.Map;
import java.util.Optional;

public final class DocumentAttributes {

  private static final DocumentAttributes EMPTY = new DocumentAttributes(Map.of());

  private final Map<String, String> values;

  private DocumentAttributes(Map<String, String> values) {
    this.values = Map.copyOf(values);
  }

  public static DocumentAttributes empty() {
    return EMPTY;
  }

  public static DocumentAttributes of(Map<String, String> values) {
    return new DocumentAttributes(values);
  }

  public Optional<String> get(String key) {
    return Optional.ofNullable(values.get(key));
  }

  /** Returns an immutable copy of all key-value pairs held by this attribute bag. */
  public Map<String, String> asMap() {
    return values;
  }
}
