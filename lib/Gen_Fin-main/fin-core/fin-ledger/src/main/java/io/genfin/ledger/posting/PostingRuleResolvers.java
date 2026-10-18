package io.genfin.ledger.posting;

import io.genfin.ledger.internal.posting.DefaultPostingRuleResolver;
import io.genfin.ledger.port.posting.PostingRuleRegistry;
import io.genfin.ledger.port.posting.PostingRuleResolver;

/** Factory for {@link PostingRuleResolver} instances. */
public final class PostingRuleResolvers {

  private PostingRuleResolvers() {}

  /** A resolver that looks up {@link PostingRuleSet}s from {@code registry}. */
  public static PostingRuleResolver of(PostingRuleRegistry registry) {
    return new DefaultPostingRuleResolver(registry);
  }
}
