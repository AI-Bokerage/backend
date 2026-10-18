package io.genfin.ledger.port.posting;

import io.genfin.ledger.fact.FinancialFact;
import io.genfin.ledger.posting.PostingContext;
import io.genfin.ledger.posting.PostingEntry;
import java.util.List;

/**
 * A {@link PostingPolicy} that resolves a {@link FinancialFact} by looking up its {@link
 * io.genfin.ledger.fact.FinancialFactType} in a {@link PostingRuleRegistry} rather than scanning a
 * flat, unkeyed list of strategies - the pluggable entry point applications wire their registered
 * {@link io.genfin.ledger.posting.PostingRuleSet}s through. Because it is itself a {@link
 * PostingPolicy}, a resolver can be registered and consumed anywhere a {@code PostingPolicy} is
 * expected, e.g. by {@code PostingEngines#from}.
 */
public interface PostingRuleResolver extends PostingPolicy {

  @Override
  List<PostingEntry> resolve(FinancialFact fact, PostingContext context);
}
