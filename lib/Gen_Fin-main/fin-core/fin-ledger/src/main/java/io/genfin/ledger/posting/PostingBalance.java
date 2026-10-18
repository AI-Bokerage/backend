package io.genfin.ledger.posting;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.money.money.Money;

/**
 * The total debits and credits posted to one {@link AccountId} within a set of {@link
 * PostingEntry}s, as computed by {@link PostingCalculator#balances}. Deliberately carries both
 * totals rather than one signed net figure - whether a debit or credit balance is "normal" for a
 * given account is a Chart of Accounts concern this module does not decide.
 */
public record PostingBalance(AccountId accountId, Money totalDebit, Money totalCredit)
    implements ValueObject {

  public PostingBalance {
    Validate.notNull(accountId, "accountId must not be null.");
    Validate.notNull(totalDebit, "totalDebit must not be null.");
    Validate.notNull(totalCredit, "totalCredit must not be null.");
  }

  /**
   * {@code totalDebit - totalCredit}; positive when the account leans debit, negative when credit.
   */
  public Money netDebit() {
    return totalDebit.subtract(totalCredit);
  }
}
