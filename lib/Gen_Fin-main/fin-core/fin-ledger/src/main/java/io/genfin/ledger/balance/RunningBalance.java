package io.genfin.ledger.balance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.money.money.Money;

/**
 * The signed, classification-normalized balance of one {@link AccountId} immediately after the
 * given {@link JournalEntryId} was posted - the running total a statement line or ledger card would
 * show. {@code amount} is already netted per {@link
 * io.genfin.ledger.account.AccountClassification#isDebitNormal()}, unlike {@link Balance} which
 * keeps both sides separate.
 */
public record RunningBalance(
    AccountId accountId, Money amount, JournalEntryId asOfEntryId, OccurredAt asOf)
    implements ValueObject {

  public RunningBalance {
    Validate.notNull(accountId, "accountId must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(asOfEntryId, "asOfEntryId must not be null.");
    Validate.notNull(asOf, "asOf must not be null.");
  }
}
