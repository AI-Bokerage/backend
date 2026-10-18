package io.genfin.ledger.balance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.account.AccountClassification;
import io.genfin.ledger.id.AccountId;
import io.genfin.money.money.Money;

/**
 * The total debits and credits posted to one {@link AccountId} across a set of posted {@link
 * io.genfin.ledger.journal.JournalEntry}s, as computed by {@link
 * io.genfin.ledger.port.balance.BalanceCalculator}. Mirrors {@code
 * io.genfin.ledger.posting.PostingBalance} - carries both totals rather than one signed figure,
 * since whether debit or credit is the "normal" side is an {@link AccountClassification} concern,
 * not this type's.
 */
public record Balance(AccountId accountId, Money totalDebit, Money totalCredit)
    implements ValueObject {

  public Balance {
    Validate.notNull(accountId, "accountId must not be null.");
    Validate.notNull(totalDebit, "totalDebit must not be null.");
    Validate.notNull(totalCredit, "totalCredit must not be null.");
  }

  /**
   * The signed net balance for {@code classification}: {@code totalDebit - totalCredit} for a
   * debit-normal classification (Asset, Expense), {@code totalCredit - totalDebit} otherwise.
   */
  public Money netAmount(AccountClassification classification) {
    Validate.notNull(classification, "classification must not be null.");
    return classification.isDebitNormal()
        ? totalDebit.subtract(totalCredit)
        : totalCredit.subtract(totalDebit);
  }
}
