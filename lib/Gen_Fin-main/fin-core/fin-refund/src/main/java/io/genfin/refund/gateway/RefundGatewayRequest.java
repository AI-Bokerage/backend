package io.genfin.refund.gateway;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.payment.method.PaymentMethod;
import io.genfin.payment.reference.Reference;
import io.genfin.refund.id.RefundId;
import io.genfin.refund.metadata.RefundMetadata;

/**
 * A provider-neutral request to refund money through the existing {@code PaymentGateway} SPI —
 * fin-refund's own type, mapped onto {@code GatewayRequest} by {@link RefundGatewayOperation}.
 * Never a raw HTTP payload, provider SDK request object, or provider refund id.
 */
public record RefundGatewayRequest(
    RefundId refundId,
    Money amount,
    PaymentMethod method,
    Reference reference,
    RefundMetadata metadata)
    implements ValueObject {

  public RefundGatewayRequest {
    Validate.notNull(refundId, "refundId must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(method, "method must not be null.");
    if (metadata == null) {
      metadata = RefundMetadata.empty();
    }
  }
}
