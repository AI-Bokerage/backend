package io.genfin.refund.metadata;

import io.genfin.api.domain.ValueObject;
import java.util.Map;
import java.util.Optional;

public record RefundMetadata(Map<String, String> values) implements ValueObject {

  public RefundMetadata {
    values = Map.copyOf(values);
  }

  public static RefundMetadata empty() {
    return new RefundMetadata(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }
}
