package io.genfin.refund.gateway;

import io.genfin.refund.internal.gateway.DefaultRefundGatewayExecutor;
import io.genfin.refund.port.gateway.RefundGatewayExecutor;

/** Factory for {@link RefundGatewayExecutor}s, mirroring {@code RefundCalculators}. */
public final class RefundGatewayExecutors {

  private static final RefundGatewayExecutor STANDARD = new DefaultRefundGatewayExecutor();

  private RefundGatewayExecutors() {}

  public static RefundGatewayExecutor standard() {
    return STANDARD;
  }
}
