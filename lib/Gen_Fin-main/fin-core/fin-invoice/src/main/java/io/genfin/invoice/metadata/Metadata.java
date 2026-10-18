package io.genfin.invoice.metadata;

import io.genfin.api.domain.ValueObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** An ordered, immutable set of {@link CustomField}s, queryable by name. */
public final class Metadata implements ValueObject {

  private static final Metadata EMPTY = new Metadata(Map.of());

  private final Map<String, TypedValue> fields;

  private Metadata(Map<String, TypedValue> fields) {
    this.fields = Map.copyOf(fields);
  }

  public static Metadata empty() {
    return EMPTY;
  }

  public static Metadata of(List<CustomField> fields) {
    Map<String, TypedValue> map = new LinkedHashMap<>();
    fields.forEach(field -> map.put(field.name(), field.value()));
    return new Metadata(map);
  }

  public Metadata with(CustomField field) {
    Map<String, TypedValue> updated = new LinkedHashMap<>(fields);
    updated.put(field.name(), field.value());
    return new Metadata(updated);
  }

  public Optional<TypedValue> find(String name) {
    return Optional.ofNullable(fields.get(name));
  }

  public Map<String, TypedValue> fields() {
    return fields;
  }

  public boolean isEmpty() {
    return fields.isEmpty();
  }
}
