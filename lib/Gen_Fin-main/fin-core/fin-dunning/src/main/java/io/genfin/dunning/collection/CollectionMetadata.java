package io.genfin.dunning.collection;

import io.genfin.api.domain.ValueObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Free-form, application-defined tags carried on a {@code DunningCase}. Mirrors {@code
 * io.genfin.ledger.account.AccountMetadata}.
 */
public record CollectionMetadata(Map<String, String> values) implements ValueObject {

  public CollectionMetadata {
    values = Map.copyOf(values);
  }

  public static CollectionMetadata empty() {
    return new CollectionMetadata(Map.of());
  }

  public CollectionMetadata with(String key, String value) {
    Map<String, String> updated = new LinkedHashMap<>(values);
    updated.put(key, value);
    return new CollectionMetadata(updated);
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }
}
