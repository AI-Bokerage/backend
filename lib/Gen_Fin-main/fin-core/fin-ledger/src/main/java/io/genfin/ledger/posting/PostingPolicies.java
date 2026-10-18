package io.genfin.ledger.posting;

import io.genfin.ledger.internal.posting.DefaultPostingPolicy;
import io.genfin.ledger.port.posting.PostingPolicy;
import io.genfin.ledger.port.posting.PostingStrategy;
import java.util.List;

/**
 * Factory for {@link PostingPolicy} instances. Deliberately has no {@code standard()} default -
 * Gen-Fin defines no built-in posting mappings, so a policy is only ever the strategies an
 * application explicitly supplies.
 */
public final class PostingPolicies {

  private PostingPolicies() {}

  /** A policy carrying no strategies at all - every {@link PostingPolicy#resolve} call fails. */
  public static PostingPolicy empty() {
    return new DefaultPostingPolicy(List.of());
  }

  /** A policy that tries {@code orderedStrategies} in order, resolving with the first that fits. */
  public static PostingPolicy of(List<PostingStrategy> orderedStrategies) {
    return new DefaultPostingPolicy(orderedStrategies);
  }
}
