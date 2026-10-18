package io.genfin.refund.policy;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.refund.port.policy.RefundPolicy;
import io.genfin.refund.reason.ReasonFactory;

/**
 * Resolves the {@link RefundPolicy} registered in an {@link ExtensionRegistry}, falling back to
 * {@link RefundPolicies#standard} scoped to the {@link ReasonFactory}-resolved reason registry.
 */
public final class RefundPolicyFactory {

  private RefundPolicyFactory() {}

  public static RefundPolicy from(ExtensionRegistry registry) {
    return registry
        .find(RefundPolicy.class)
        .orElseGet(() -> RefundPolicies.standard(ReasonFactory.from(registry)));
  }
}
