package io.genfin.ledger.port.posting;

import io.genfin.ledger.posting.PostingRuleSet;
import java.util.List;

/**
 * Supplies the catalog of {@link PostingRuleSet}s a deployment recognizes. Gen-Fin ships no default
 * implementation - unlike account types or discrepancy reasons that may have a standard catalog,
 * which fact types exist and how each one posts is entirely the consuming application's own
 * business rule. Mirrors {@code io.genfin.ledger.port.account.AccountTypeProvider}.
 */
@FunctionalInterface
public interface PostingRuleProvider {

  List<PostingRuleSet> provide();
}
