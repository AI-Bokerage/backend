package io.genfin.refund.gateway;

import io.genfin.api.domain.ValueObject;
import io.genfin.payment.failure.FailureReason;
import io.genfin.refund.metadata.RefundMetadata;
import java.util.Optional;

/**
 * A provider-neutral outcome of a refund gateway call — fin-refund's own type, produced by {@link
 * RefundGatewayOperation} from the existing {@code GatewayResponse}. Never a raw gateway SDK
 * response, HTTP payload, or provider refund id.
 */
public record RefundGatewayResponse(
    boolean success, String gatewayReference, FailureReason failureReason, RefundMetadata metadata)
    implements ValueObject {

  public static RefundGatewayResponse success(String gatewayReference, RefundMetadata metadata) {
    return new RefundGatewayResponse(
        true, gatewayReference, null, metadata == null ? RefundMetadata.empty() : metadata);
  }

  public static RefundGatewayResponse failure(FailureReason reason) {
    return new RefundGatewayResponse(false, null, reason, RefundMetadata.empty());
  }

  public Optional<String> reference() {
    return Optional.ofNullable(gatewayReference);
  }

  public Optional<FailureReason> failure() {
    return Optional.ofNullable(failureReason);
  }
}
