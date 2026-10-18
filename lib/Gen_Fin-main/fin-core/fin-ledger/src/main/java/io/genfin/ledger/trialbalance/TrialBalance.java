package io.genfin.ledger.trialbalance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.AccountingPeriodId;
import java.util.List;
import java.util.Optional;

/**
 * The full per-account debit/credit roll-up a {@link
 * io.genfin.ledger.port.trialbalance.TrialBalanceCalculator} derived for one {@link
 * io.genfin.ledger.period.AccountingPeriod}, plus its grand-total {@link TrialBalanceSummary}. A
 * pure derived read model - it owns nothing, it is recomputed from posted {@link
 * io.genfin.ledger.journal.JournalEntry}s whenever needed rather than persisted or mutated.
 */
public record TrialBalance(
    AccountingPeriodId periodId, List<TrialBalanceEntry> entries, TrialBalanceSummary summary)
    implements ValueObject {

  public TrialBalance {
    Validate.notNull(periodId, "periodId must not be null.");
    entries = CollectionUtils.immutableList(entries);
    Validate.notNull(summary, "summary must not be null.");
  }

  public Optional<TrialBalanceEntry> find(AccountId accountId) {
    return entries.stream().filter(entry -> entry.accountId().equals(accountId)).findFirst();
  }
}
