package io.genfin.ledger.internal.posting;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.fact.FinancialFact;
import io.genfin.ledger.port.posting.PostingRuleRegistry;
import io.genfin.ledger.port.posting.PostingRuleResolver;
import io.genfin.ledger.port.posting.PostingStrategy;
import io.genfin.ledger.posting.PostingContext;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.posting.PostingRuleSet;
import java.util.List;

/**
 * Looks up the {@link PostingRuleSet} registered for the fact's {@link
 * io.genfin.ledger.fact.FinancialFactType} and tries each of its {@link PostingStrategy}s in order,
 * resolving with the first one that supports the fact. Mirrors {@code
 * io.genfin.reconciliation.internal.matching.DefaultMatchingPolicy} / {@code
 * io.genfin.ledger.internal.posting.DefaultPostingPolicy}, but keyed lookup first instead of a flat
 * scan over every registered strategy.
 */
public final class DefaultPostingRuleResolver implements PostingRuleResolver {

  private final PostingRuleRegistry registry;

  public DefaultPostingRuleResolver(PostingRuleRegistry registry) {
    this.registry = Validate.notNull(registry, "registry must not be null.");
  }

  @Override
  public List<PostingEntry> resolve(FinancialFact fact, PostingContext context) {
    Validate.notNull(fact, "fact must not be null.");
    Validate.notNull(context, "context must not be null.");

    PostingRuleSet ruleSet = registry.require(fact.factType());
    for (PostingStrategy rule : ruleSet.rules()) {
      if (rule.supports(fact)) {
        return rule.resolve(fact, context);
      }
    }
    throw new IllegalStateException(
        "No PostingRule in the registered PostingRuleSet for fact type "
            + fact.factType().code()
            + " supports the fact.");
  }
}
