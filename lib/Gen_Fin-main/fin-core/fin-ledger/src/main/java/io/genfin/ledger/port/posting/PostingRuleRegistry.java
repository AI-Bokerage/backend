package io.genfin.ledger.port.posting;

import io.genfin.api.exception.ValidationException;
import io.genfin.ledger.fact.FinancialFactType;
import io.genfin.ledger.posting.PostingRuleSet;
import java.util.List;
import java.util.Optional;

/**
 * Registry of the {@link PostingRuleSet}s an application has registered, keyed by {@link
 * FinancialFactType}. Mirrors {@code io.genfin.ledger.port.account.AccountTypeRegistry}.
 */
public interface PostingRuleRegistry {

  void register(PostingRuleSet ruleSet);

  Optional<PostingRuleSet> find(FinancialFactType factType);

  default PostingRuleSet require(FinancialFactType factType) {
    return find(factType)
        .orElseThrow(
            () ->
                new ValidationException(
                    "No PostingRuleSet registered for fact type " + factType.code() + "."));
  }

  List<PostingRuleSet> findAll();
}
