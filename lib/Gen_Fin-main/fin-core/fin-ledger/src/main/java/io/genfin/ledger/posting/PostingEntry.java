package io.genfin.ledger.posting;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.JournalLineId;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.money.money.Money;

/**
 * One leg of a posting - an amount posted to one side of one account - before it becomes a {@link
 * JournalLine}. This is the shape {@link io.genfin.ledger.port.posting.PostingStrategy}s produce
 * and {@link io.genfin.ledger.port.posting.PostingValidator} checks for balance; the {@link
 * io.genfin.ledger.port.posting.PostingEngine} converts a balanced set of these into the {@link
 * JournalLine}s of the {@link io.genfin.ledger.journal.JournalEntry} it posts.
 */
public record PostingEntry(AccountId accountId, PostingSide side, Money amount, String memo)
    implements ValueObject {

  public PostingEntry {
    Validate.notNull(accountId, "accountId must not be null.");
    Validate.notNull(side, "side must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.argument(amount.isPositive(), "a posting entry amount must be positive.");
    if (memo == null) {
      memo = "";
    }
  }

  public static PostingEntry of(AccountId accountId, Debit debit) {
    return of(accountId, debit, "");
  }

  public static PostingEntry of(AccountId accountId, Debit debit, String memo) {
    Validate.notNull(debit, "debit must not be null.");
    return new PostingEntry(accountId, PostingSide.DEBIT, debit.amount(), memo);
  }

  public static PostingEntry of(AccountId accountId, Credit credit) {
    return of(accountId, credit, "");
  }

  public static PostingEntry of(AccountId accountId, Credit credit, String memo) {
    Validate.notNull(credit, "credit must not be null.");
    return new PostingEntry(accountId, PostingSide.CREDIT, credit.amount(), memo);
  }

  public boolean isDebit() {
    return side == PostingSide.DEBIT;
  }

  /** Converts this entry into a {@link JournalLine} carrying the given identity. */
  public JournalLine toJournalLine(JournalLineId id) {
    return isDebit()
        ? JournalLine.debit(id, accountId, amount, memo)
        : JournalLine.credit(id, accountId, amount, memo);
  }
}
