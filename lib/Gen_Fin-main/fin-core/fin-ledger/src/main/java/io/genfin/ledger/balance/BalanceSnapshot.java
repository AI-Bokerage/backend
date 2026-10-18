package io.genfin.ledger.balance;

import io.genfin.api.domain.Entity;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.BalanceSnapshotId;
import io.genfin.ledger.id.LedgerId;
import java.util.List;

/**
 * An immutable, point-in-time capture of every {@link Balance} in a {@code Ledger}, taken at {@link
 * #takenAt()}. Once built, a snapshot never changes - a later state of the ledger is always a new
 * snapshot appended to a {@link BalanceHistory}, never a mutation of this one.
 */
public final class BalanceSnapshot extends Entity<BalanceSnapshotId> {

  private final LedgerId ledgerId;
  private final List<Balance> balances;
  private final OccurredAt takenAt;

  public BalanceSnapshot(
      BalanceSnapshotId id, LedgerId ledgerId, List<Balance> balances, OccurredAt takenAt) {
    super(id);
    this.ledgerId = Validate.notNull(ledgerId, "ledgerId must not be null.");
    this.balances = CollectionUtils.immutableList(balances);
    this.takenAt = Validate.notNull(takenAt, "takenAt must not be null.");
  }

  public static BalanceSnapshot of(LedgerId ledgerId, List<Balance> balances, OccurredAt takenAt) {
    return new BalanceSnapshot(BalanceSnapshotId.generate(), ledgerId, balances, takenAt);
  }

  public LedgerId ledgerId() {
    return ledgerId;
  }

  public List<Balance> balances() {
    return balances;
  }

  public OccurredAt takenAt() {
    return takenAt;
  }
}
