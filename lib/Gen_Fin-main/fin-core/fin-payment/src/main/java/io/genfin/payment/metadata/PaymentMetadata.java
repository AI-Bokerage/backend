package io.genfin.payment.metadata;

import io.genfin.api.domain.ValueObject;
import java.util.Map;
import java.util.Optional;

public record PaymentMetadata(Map<String, String> values) implements ValueObject {

  public PaymentMetadata {
    values = Map.copyOf(values);
  }

  public static PaymentMetadata empty() {
    return new PaymentMetadata(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }
}
