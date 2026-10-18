package io.genfin.ledger.posting;

import io.genfin.ledger.internal.posting.DefaultPostingValidator;
import io.genfin.ledger.port.posting.PostingRule;
import io.genfin.ledger.port.posting.PostingValidator;
import java.util.List;

/** Factory for {@link PostingValidator} instances. */
public final class PostingValidators {

  private PostingValidators() {}

  /** Enforces the hard balance invariant only, no additional {@link PostingRule}s. */
  public static PostingValidator standard() {
    return new DefaultPostingValidator(List.of());
  }

  /** Enforces the hard balance invariant plus every rule in {@code rules}. */
  public static PostingValidator withRules(List<PostingRule> rules) {
    return new DefaultPostingValidator(rules);
  }
}
