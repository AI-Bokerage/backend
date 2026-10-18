package io.genfin.dunning.port.rule;

import java.util.List;

/**
 * Supplies the catalog of {@link CollectionRule}s a deployment wants a {@link CollectionRuleEngine}
 * to run. Gen-Fin ships only illustrative examples (see {@code
 * io.genfin.dunning.rule.CollectionRules}) - which rules actually gate collection for a given
 * business (which retry counts matter, which attributes pause a case, which calendar to skip
 * holidays against, what counts as a priority amount) is entirely the consuming application's own
 * decision. Mirrors {@code io.genfin.ledger.port.posting.PostingRuleProvider}.
 */
@FunctionalInterface
public interface CollectionRuleProvider {

  List<CollectionRule> provide();
}
