package io.genfin.ledger.reversal;

import io.genfin.ledger.internal.reversal.DefaultReversalPolicy;
import io.genfin.ledger.port.reversal.ReversalPolicy;
import io.genfin.ledger.port.reversal.ReversalReasonRegistry;

/** Factory for {@link ReversalPolicy} instances. Mirrors {@code RefundPolicies.reasonPolicy}. */
public final class ReversalPolicies {

  private ReversalPolicies() {}

  public static ReversalPolicy of(ReversalReasonRegistry registry) {
    return new DefaultReversalPolicy(registry);
  }

  /** A policy permitting exactly {@link ReversalReasonRegistries#standardCatalog()}. */
  public static ReversalPolicy standard() {
    return of(ReversalReasonRegistries.withProvider(ReversalReasonRegistries.standardCatalog()));
  }
}
