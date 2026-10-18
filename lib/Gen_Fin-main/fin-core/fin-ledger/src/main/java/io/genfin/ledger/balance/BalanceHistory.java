package io.genfin.ledger.balance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import java.util.ArrayList;
import java.util.List;

/**
 * The append-only sequence of {@link BalanceSnapshot}s taken over a {@code Ledger}'s lifetime.
 * Never shrinks and never rewrites a past snapshot - mirrors {@code
 * io.genfin.ledger.journal.JournalHistory}: a correction is always a new snapshot appended after a
 * new posting, the old one stays intact.
 */
public record BalanceHistory(List<BalanceSnapshot> snapshots) implements ValueObject {

  public BalanceHistory {
    snapshots = CollectionUtils.immutableList(snapshots);
    Validate.argument(!snapshots.isEmpty(), "history must not be empty.");
  }

  public static BalanceHistory initial(BalanceSnapshot snapshot) {
    return new BalanceHistory(List.of(Validate.notNull(snapshot, "snapshot must not be null.")));
  }

  public BalanceHistory append(BalanceSnapshot snapshot) {
    List<BalanceSnapshot> updated = new ArrayList<>(snapshots);
    updated.add(Validate.notNull(snapshot, "snapshot must not be null."));
    return new BalanceHistory(updated);
  }

  public BalanceSnapshot latest() {
    return snapshots.get(snapshots.size() - 1);
  }
}
