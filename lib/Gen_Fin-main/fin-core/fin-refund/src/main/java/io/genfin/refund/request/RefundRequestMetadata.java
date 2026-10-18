package io.genfin.refund.request;

import io.genfin.api.domain.ValueObject;
import java.util.Map;
import java.util.Optional;

/** Free-form metadata on a {@link RefundRequest}, kept separate from the executed refund's. */
public record RefundRequestMetadata(Map<String, String> values) implements ValueObject {

  public RefundRequestMetadata {
    values = Map.copyOf(values);
  }

  public static RefundRequestMetadata empty() {
    return new RefundRequestMetadata(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }
}
