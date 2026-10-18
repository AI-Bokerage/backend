package io.genfin.ledger.port.posting;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.fact.FinancialFact;
import io.genfin.ledger.posting.PostingContext;
import io.genfin.ledger.posting.PostingEntry;
import java.util.List;

/**
 * The single entry point for resolving a {@link FinancialFact} to the {@link PostingEntry} legs it
 * posts: composes an ordered set of {@link PostingStrategy} implementations so callers depend on
 * one SPI instead of picking a strategy themselves. Mirrors {@code
 * io.genfin.reconciliation.port.matching.MatchingPolicy}.
 */
public interface PostingPolicy extends Extension {

  List<PostingEntry> resolve(FinancialFact fact, PostingContext context);
}
