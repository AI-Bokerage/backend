package io.genfin.ledger.trialbalance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.money.money.Money;

/**
 * The total debits and credits posted to one {@link AccountId} across every posted {@link
 * io.genfin.ledger.journal.JournalEntry} a {@link
 * io.genfin.ledger.port.trialbalance.TrialBalanceCalculator} rolled up for one {@link
 * io.genfin.ledger.period.AccountingPeriod}. Deliberately carries both totals rather than one
 * signed net figure, mirroring {@code io.genfin.ledger.posting.PostingBalance} - whether a debit or
 * credit balance is "normal" for a given account is a Chart of Accounts concern this type does not
 * decide.
 */
public record TrialBalanceEntry(AccountId accountId, Money totalDebit, Money totalCredit)
    implements ValueObject {

  public TrialBalanceEntry {
    Validate.notNull(accountId, "accountId must not be null.");
    Validate.notNull(totalDebit, "totalDebit must not be null.");
    Validate.notNull(totalCredit, "totalCredit must not be null.");
  }

  /** {@code totalDebit - totalCredit}; positive when the account leans debit, negative credit. */
  public Money netDebit() {
    return totalDebit.subtract(totalCredit);
  }
}
