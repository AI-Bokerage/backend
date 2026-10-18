package io.genfin.refund.refund;

import io.genfin.refund.internal.refund.DefaultRefundBuilderProvider;
import io.genfin.refund.port.refund.RefundBuilderProvider;

/** Factory for {@link RefundBuilderProvider}s, mirroring {@code RefundCalculators}. */
public final class RefundBuilders {

  private static final RefundBuilderProvider STANDARD = new DefaultRefundBuilderProvider();

  private RefundBuilders() {}

  public static RefundBuilderProvider standard() {
    return STANDARD;
  }
}
