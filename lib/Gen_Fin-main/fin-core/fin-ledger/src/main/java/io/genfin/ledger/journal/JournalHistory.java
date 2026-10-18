package io.genfin.ledger.journal;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.lifecycle.LedgerStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * The append-only sequence of lifecycle states a {@link JournalEntry} has passed through. Never
 * shrinks and never rewrites a past entry - corrections ({@link JournalEntry#reverse()}, {@link
 * JournalEntry#adjust()}) only ever append a further transition.
 */
public record JournalHistory(List<LedgerStatus> transitions) implements ValueObject {

  public JournalHistory {
    transitions = CollectionUtils.immutableList(transitions);
    Validate.argument(!transitions.isEmpty(), "history must not be empty.");
  }

  public static JournalHistory initial(LedgerStatus status) {
    return new JournalHistory(List.of(Validate.notNull(status, "status must not be null.")));
  }

  public JournalHistory append(LedgerStatus status) {
    List<LedgerStatus> updated = new ArrayList<>(transitions);
    updated.add(Validate.notNull(status, "status must not be null."));
    return new JournalHistory(updated);
  }

  public LedgerStatus current() {
    return transitions.get(transitions.size() - 1);
  }
}
