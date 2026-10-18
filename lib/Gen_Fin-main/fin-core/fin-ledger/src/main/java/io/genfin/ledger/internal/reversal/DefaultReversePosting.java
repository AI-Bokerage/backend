package io.genfin.ledger.internal.reversal;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.port.reversal.ReversePosting;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.posting.PostingSide;

/** Swaps the debit/credit side of a {@link JournalLine} while keeping account and amount. */
public final class DefaultReversePosting implements ReversePosting {

  private static final String MEMO_PREFIX = "Reversal of: ";

  @Override
  public PostingEntry reverse(JournalLine line) {
    Validate.notNull(line, "line must not be null.");
    String memo = MEMO_PREFIX + line.memo();
    return line.isDebit()
        ? new PostingEntry(line.accountId(), PostingSide.CREDIT, line.debit(), memo)
        : new PostingEntry(line.accountId(), PostingSide.DEBIT, line.credit(), memo);
  }
}
