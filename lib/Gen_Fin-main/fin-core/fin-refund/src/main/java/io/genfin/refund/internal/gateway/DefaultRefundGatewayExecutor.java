package io.genfin.refund.internal.gateway;

import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.refund.gateway.RefundGatewayOperation;
import io.genfin.refund.gateway.RefundGatewayRequest;
import io.genfin.refund.gateway.RefundGatewayResponse;
import io.genfin.refund.port.gateway.RefundGatewayExecutor;

/**
 * Default {@link RefundGatewayExecutor}: a direct pass-through to {@link RefundGatewayOperation}.
 */
public final class DefaultRefundGatewayExecutor implements RefundGatewayExecutor {

  @Override
  public RefundGatewayResponse execute(PaymentGateway gateway, RefundGatewayRequest request) {
    return RefundGatewayOperation.execute(gateway, request);
  }
}
