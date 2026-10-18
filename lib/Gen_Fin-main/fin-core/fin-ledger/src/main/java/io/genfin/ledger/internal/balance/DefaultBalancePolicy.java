package io.genfin.ledger.internal.balance;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.lifecycle.LedgerStatus;
import io.genfin.ledger.lifecycle.StandardLedgerStatus;
import io.genfin.ledger.port.balance.BalancePolicy;
import java.util.Set;

/**
 * The standard {@link BalancePolicy}: includes every entry that has actually been posted, whatever
 * became of it afterwards. A reversed entry is not excluded - it stays intact and its reversal is a
 * separate, additional entry with opposite lines, so including both is what makes them net back to
 * zero rather than the calculator having to special-case reversal itself.
 */
public final class DefaultBalancePolicy implements BalancePolicy {

  private static final Set<LedgerStatus> EXCLUDED =
      Set.of(
          StandardLedgerStatus.CREATED,
          StandardLedgerStatus.VALIDATED,
          StandardLedgerStatus.FAILED);

  @Override
  public boolean includes(JournalEntry entry) {
    Validate.notNull(entry, "entry must not be null.");
    return !EXCLUDED.contains(entry.status());
  }
}
