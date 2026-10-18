package io.genfin.refund.gateway;

import io.genfin.api.validation.Validate;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.gateway.GatewayResponse;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.refund.metadata.RefundMetadata;

/**
 * Executes a refund through the existing {@link PaymentGateway#refund(GatewayRequest)} — the only
 * point where fin-refund's own {@link RefundGatewayRequest}/{@link RefundGatewayResponse} cross
 * over to fin-payment's generic {@code GatewayRequest}/{@code GatewayResponse}. No provider SDK
 * type, HTTP concept, or raw gateway response ever leaves this class.
 */
public final class RefundGatewayOperation {

  private RefundGatewayOperation() {}

  public static RefundGatewayResponse execute(
      PaymentGateway gateway, RefundGatewayRequest request) {
    Validate.notNull(gateway, "gateway must not be null.");
    Validate.notNull(request, "request must not be null.");

    GatewayResponse response = gateway.refund(toGatewayRequest(request));

    return response.success()
        ? RefundGatewayResponse.success(
            response.gatewayReference(), toRefundMetadata(response.metadata()))
        : RefundGatewayResponse.failure(response.failureReason());
  }

  private static GatewayRequest toGatewayRequest(RefundGatewayRequest request) {
    return new GatewayRequest(
        request.amount(),
        request.method(),
        request.reference(),
        IdempotencyKey.of(request.refundId().value()),
        toPaymentMetadata(request.metadata()));
  }

  private static PaymentMetadata toPaymentMetadata(RefundMetadata metadata) {
    return metadata == null ? PaymentMetadata.empty() : new PaymentMetadata(metadata.values());
  }

  private static RefundMetadata toRefundMetadata(PaymentMetadata metadata) {
    return metadata == null ? RefundMetadata.empty() : new RefundMetadata(metadata.values());
  }
}
