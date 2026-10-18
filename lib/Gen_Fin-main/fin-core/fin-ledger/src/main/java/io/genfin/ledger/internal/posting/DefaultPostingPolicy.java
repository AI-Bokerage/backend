package io.genfin.ledger.internal.posting;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.fact.FinancialFact;
import io.genfin.ledger.port.posting.PostingPolicy;
import io.genfin.ledger.port.posting.PostingStrategy;
import io.genfin.ledger.posting.PostingContext;
import io.genfin.ledger.posting.PostingEntry;
import java.util.List;

/**
 * Tries each configured {@link PostingStrategy} in order and resolves with the first one that
 * {@link PostingStrategy#supports supports} the fact, mirroring {@code
 * io.genfin.reconciliation.internal.matching.DefaultMatchingPolicy}.
 */
public final class DefaultPostingPolicy implements PostingPolicy {

  private final List<PostingStrategy> strategies;

  public DefaultPostingPolicy(List<PostingStrategy> strategies) {
    this.strategies = List.copyOf(strategies);
  }

  @Override
  public List<PostingEntry> resolve(FinancialFact fact, PostingContext context) {
    Validate.notNull(fact, "fact must not be null.");
    Validate.notNull(context, "context must not be null.");
    for (PostingStrategy strategy : strategies) {
      if (strategy.supports(fact)) {
        return strategy.resolve(fact, context);
      }
    }
    throw new IllegalStateException(
        "No PostingStrategy registered for fact type " + fact.factType().code() + ".");
  }
}
