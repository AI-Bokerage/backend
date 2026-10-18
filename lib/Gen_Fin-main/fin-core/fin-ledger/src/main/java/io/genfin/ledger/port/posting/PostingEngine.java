package io.genfin.ledger.port.posting;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.fact.FinancialFact;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.posting.PostingContext;

/**
 * The Double-Entry Posting Engine: turns one {@link FinancialFact} into a balanced {@link
 * JournalEntry}, by resolving it through a {@link PostingPolicy} and rejecting the result outright
 * if a {@link PostingValidator} finds it invalid - callers never receive an unbalanced entry.
 */
public interface PostingEngine extends Extension {

  JournalEntry post(FinancialFact fact, LedgerId ledgerId, PostingContext context);
}
