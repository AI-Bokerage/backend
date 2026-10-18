package io.genfin.refund.internal.refund;

import io.genfin.refund.port.refund.RefundBuilderProvider;
import io.genfin.refund.refund.RefundBuilder;

/** Default {@link RefundBuilderProvider}: a plain, unconfigured {@link RefundBuilder} each call. */
public final class DefaultRefundBuilderProvider implements RefundBuilderProvider {

  @Override
  public RefundBuilder newBuilder() {
    return RefundBuilder.newRefund();
  }
}
