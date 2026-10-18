package io.genfin.refund.refund;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.money.money.Money;
import io.genfin.refund.lifecycle.RefundLifecycles;
import io.genfin.refund.lifecycle.StandardRefundStatus;
import io.genfin.refund.port.lifecycle.RefundLifecycleProvider;
import io.genfin.refund.reference.Reference;

/**
 * Creates {@link Refund}s wired to the {@link RefundLifecycleProvider} registered in an {@link
 * ExtensionRegistry}, falling back to {@link RefundLifecycles#standard()}. Preferred over {@link
 * RefundBuilder} directly whenever the lifecycle should come from the deployment's configured
 * extensions rather than the built-in default.
 */
public final class RefundFactory {

  private RefundFactory() {}

  public static Refund create(
      ExtensionRegistry registry,
      RefundNumber refundNumber,
      Money amount,
      RefundType type,
      RefundDirection direction,
      Reference paymentReference) {
    RefundLifecycleProvider lifecycleProvider =
        registry.find(RefundLifecycleProvider.class).orElseGet(RefundLifecycles::standard);
    return RefundBuilder.newRefund()
        .refundNumber(refundNumber)
        .amount(amount)
        .type(type)
        .direction(direction)
        .paymentReference(paymentReference)
        .lifecycle(lifecycleProvider.create(StandardRefundStatus.REQUESTED))
        .build();
  }
}
