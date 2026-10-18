package io.genfin.ledger.port.posting;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.fact.FinancialFact;
import io.genfin.ledger.posting.PostingContext;
import io.genfin.ledger.posting.PostingEntry;
import java.util.List;

/**
 * One application-registered way of turning a {@link FinancialFact} into the {@link PostingEntry}
 * legs it posts - e.g. "invoice paid" -> debit Cash / credit Accounts Receivable. Gen-Fin ships no
 * implementations: which accounts a fact posts to, and under what mapping, is entirely the
 * consuming application's own business rule, registered here rather than hardcoded.
 */
public interface PostingStrategy extends Extension {

  /** Whether this strategy knows how to post {@code fact}. */
  boolean supports(FinancialFact fact);

  /** The balanced set of legs {@code fact} posts, given {@code context}. */
  List<PostingEntry> resolve(FinancialFact fact, PostingContext context);
}
