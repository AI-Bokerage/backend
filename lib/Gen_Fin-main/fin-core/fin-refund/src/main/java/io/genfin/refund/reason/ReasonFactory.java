package io.genfin.refund.reason;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.refund.port.reason.RefundReasonProvider;
import io.genfin.refund.port.reason.RefundReasonRegistry;

/**
 * Resolves the {@link RefundReasonRegistry} for a deployment: the {@link RefundReasonProvider}
 * registered in {@code registry}, falling back to {@link RefundReasonRegistries#standardCatalog()}.
 */
public final class ReasonFactory {

  private ReasonFactory() {}

  public static RefundReasonRegistry from(ExtensionRegistry registry) {
    RefundReasonProvider provider =
        registry
            .find(RefundReasonProvider.class)
            .orElseGet(RefundReasonRegistries::standardCatalog);
    return RefundReasonRegistries.withProvider(provider);
  }
}
