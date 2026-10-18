package io.genfin.ledger.posting;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.fact.FinancialFactType;
import io.genfin.ledger.port.posting.PostingStrategy;
import java.util.List;

/**
 * Every {@link PostingStrategy} an application has registered for one {@link FinancialFactType},
 * tried in order until one {@link PostingStrategy#supports supports} the fact - e.g. an application
 * may register more than one strategy per fact type to refine on metadata the plain fact type
 * doesn't capture. Gen-Fin defines no rule sets of its own; every {@link FinancialFactType} and its
 * strategies are entirely the consuming application's own business rules.
 */
public record PostingRuleSet(FinancialFactType factType, List<PostingStrategy> rules)
    implements ValueObject {

  public PostingRuleSet {
    Validate.notNull(factType, "factType must not be null.");
    Validate.notNull(rules, "rules must not be null.");
    Validate.argument(!rules.isEmpty(), "rules must not be empty.");
    rules = CollectionUtils.immutableList(rules);
  }

  public static PostingRuleSet of(FinancialFactType factType, PostingStrategy... rules) {
    return new PostingRuleSet(factType, List.of(rules));
  }
}
