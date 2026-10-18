package io.genfin.ledger.account;

import io.genfin.api.domain.ValueObject;
import java.util.Map;
import java.util.Optional;

/**
 * Free-form, application-defined tags carried on an {@link Account}. Mirrors {@code
 * io.genfin.refund.metadata.RefundMetadata}.
 */
public record AccountMetadata(Map<String, String> values) implements ValueObject {

  public AccountMetadata {
    values = Map.copyOf(values);
  }

  public static AccountMetadata empty() {
    return new AccountMetadata(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }
}
