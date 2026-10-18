package io.genfin.ledger.journal;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import java.util.Map;
import java.util.Optional;

/**
 * Free-form, application-defined tags carried on a {@link JournalEntry} (e.g. a memo, an external
 * batch id, ...). Mirrors {@code io.genfin.ledger.account.AccountMetadata} / {@code
 * io.genfin.refund.metadata.RefundMetadata}.
 */
public record JournalMetadata(Map<String, String> values) implements ValueObject {

  public JournalMetadata {
    values = CollectionUtils.immutableMap(values);
  }

  public static JournalMetadata empty() {
    return new JournalMetadata(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }
}
