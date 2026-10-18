package io.genfin.refund.port.gateway;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.refund.gateway.RefundGatewayRequest;
import io.genfin.refund.gateway.RefundGatewayResponse;

/**
 * Executes a {@link RefundGatewayRequest} against a {@link PaymentGateway}. The default
 * implementation simply delegates to {@code RefundGatewayOperation.execute}; the extension point
 * exists so a deployment can wrap the call (retries, logging, metrics) without ever introducing a
 * provider-specific refund interface.
 */
public interface RefundGatewayExecutor extends Extension {

  RefundGatewayResponse execute(PaymentGateway gateway, RefundGatewayRequest request);
}
