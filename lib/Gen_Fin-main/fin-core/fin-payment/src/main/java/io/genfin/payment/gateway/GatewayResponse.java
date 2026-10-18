package io.genfin.payment.gateway;

import io.genfin.api.domain.ValueObject;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.metadata.PaymentMetadata;
import java.util.Optional;

/** A provider-neutral response — never a raw HTTP response, gateway SDK object, or JSON payload. */
public record GatewayResponse(
    boolean success, String gatewayReference, FailureReason failureReason, PaymentMetadata metadata)
    implements ValueObject {

  public static GatewayResponse success(String gatewayReference, PaymentMetadata metadata) {
    return new GatewayResponse(
        true, gatewayReference, null, metadata == null ? PaymentMetadata.empty() : metadata);
  }

  public static GatewayResponse failure(FailureReason reason) {
    return new GatewayResponse(false, null, reason, PaymentMetadata.empty());
  }

  public Optional<String> reference() {
    return Optional.ofNullable(gatewayReference);
  }

  public Optional<FailureReason> failure() {
    return Optional.ofNullable(failureReason);
  }
}
