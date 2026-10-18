package io.genfin.ledger.journal;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.JournalLineId;
import io.genfin.money.money.Money;

/**
 * A single debit-or-credit posting against one {@link AccountId} within a {@link JournalEntry}.
 * Exactly one of {@link #debit()} / {@link #credit()} is a positive amount, the other the zero
 * amount of the same currency - this is enforced here, by construction, rather than left to callers
 * to get right.
 */
public record JournalLine(
    JournalLineId id, AccountId accountId, Money debit, Money credit, String memo)
    implements ValueObject {

  public JournalLine {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(accountId, "accountId must not be null.");
    Validate.notNull(debit, "debit must not be null.");
    Validate.notNull(credit, "credit must not be null.");
    Validate.argument(!debit.isNegative(), "debit must not be negative.");
    Validate.argument(!credit.isNegative(), "credit must not be negative.");
    Validate.argument(
        debit.isZero() != credit.isZero(),
        "a journal line must be either a debit or a credit, not both or neither.");
    if (memo == null) {
      memo = "";
    }
  }

  public static JournalLine debit(JournalLineId id, AccountId accountId, Money amount) {
    return debit(id, accountId, amount, "");
  }

  public static JournalLine debit(
      JournalLineId id, AccountId accountId, Money amount, String memo) {
    return new JournalLine(id, accountId, amount, Money.zero(amount.currency()), memo);
  }

  public static JournalLine credit(JournalLineId id, AccountId accountId, Money amount) {
    return credit(id, accountId, amount, "");
  }

  public static JournalLine credit(
      JournalLineId id, AccountId accountId, Money amount, String memo) {
    return new JournalLine(id, accountId, Money.zero(amount.currency()), amount, memo);
  }

  public boolean isDebit() {
    return !debit.isZero();
  }
}
