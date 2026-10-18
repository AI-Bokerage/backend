package io.genfin.refund.reason;

import io.genfin.refund.internal.reason.DefaultRefundReasonRegistry;
import io.genfin.refund.port.reason.RefundReasonProvider;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import java.util.List;

public final class RefundReasonRegistries {

  private RefundReasonRegistries() {}

  public static RefundReasonRegistry empty() {
    return new DefaultRefundReasonRegistry();
  }

  public static RefundReasonRegistry withProvider(RefundReasonProvider provider) {
    RefundReasonRegistry registry = new DefaultRefundReasonRegistry();
    provider.provide().forEach(registry::register);
    return registry;
  }

  public static RefundReasonProvider standardCatalog() {
    return () ->
        List.of(
            new RefundReasonDescriptor(StandardRefundReason.CUSTOMER_REQUEST, "Customer Request"),
            new RefundReasonDescriptor(StandardRefundReason.DUPLICATE_CHARGE, "Duplicate Charge"),
            new RefundReasonDescriptor(StandardRefundReason.FRAUD, "Fraud"),
            new RefundReasonDescriptor(StandardRefundReason.PRODUCT_RETURN, "Product Return"),
            new RefundReasonDescriptor(
                StandardRefundReason.PRICING_ADJUSTMENT, "Pricing Adjustment"),
            new RefundReasonDescriptor(StandardRefundReason.SERVICE_FAILURE, "Service Failure"),
            new RefundReasonDescriptor(StandardRefundReason.MANUAL_CORRECTION, "Manual Correction"),
            new RefundReasonDescriptor(
                StandardRefundReason.CHARGEBACK_RESOLUTION, "Chargeback Resolution"));
  }
}
