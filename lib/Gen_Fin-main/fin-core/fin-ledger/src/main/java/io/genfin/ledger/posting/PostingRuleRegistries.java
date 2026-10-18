package io.genfin.ledger.posting;

import io.genfin.ledger.internal.posting.DefaultPostingRuleRegistry;
import io.genfin.ledger.port.posting.PostingRuleProvider;
import io.genfin.ledger.port.posting.PostingRuleRegistry;

/**
 * Factory for {@link PostingRuleRegistry} instances. Deliberately has no {@code standardCatalog()}
 * counterpart - Gen-Fin defines no built-in fact types or posting mappings, so every registry
 * starts empty until an application supplies its own {@link PostingRuleProvider}. Mirrors {@code
 * io.genfin.ledger.account.AccountTypeRegistries}.
 */
public final class PostingRuleRegistries {

  private PostingRuleRegistries() {}

  public static PostingRuleRegistry empty() {
    return new DefaultPostingRuleRegistry();
  }

  public static PostingRuleRegistry withProvider(PostingRuleProvider provider) {
    PostingRuleRegistry registry = new DefaultPostingRuleRegistry();
    provider.provide().forEach(registry::register);
    return registry;
  }
}
