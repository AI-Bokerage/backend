package io.genfin.ledger.internal.posting;

import io.genfin.ledger.fact.FinancialFactType;
import io.genfin.ledger.port.posting.PostingRuleRegistry;
import io.genfin.ledger.posting.PostingRuleSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Mirrors {@code io.genfin.ledger.internal.account.DefaultAccountTypeRegistry}. */
public final class DefaultPostingRuleRegistry implements PostingRuleRegistry {

  private final ConcurrentMap<String, PostingRuleSet> ruleSets = new ConcurrentHashMap<>();

  @Override
  public void register(PostingRuleSet ruleSet) {
    ruleSets.put(ruleSet.factType().code(), ruleSet);
  }

  @Override
  public Optional<PostingRuleSet> find(FinancialFactType factType) {
    return Optional.ofNullable(ruleSets.get(factType.code()));
  }

  @Override
  public List<PostingRuleSet> findAll() {
    return List.copyOf(ruleSets.values());
  }
}
