package io.genfin.payment.gateway;

import io.genfin.api.domain.ValueObject;
import io.genfin.payment.method.PaymentMethodType;
import java.util.Set;

/**
 * What operations a gateway's API supports — as opposed to {@link ProviderCapabilities}, which
 * describes the business behind it.
 */
public record GatewayCapabilities(
    boolean supportsPartialCapture,
    boolean supportsMultipleCapture,
    boolean supportsRefund,
    boolean supportsVoid,
    boolean supportsRecurring,
    Set<PaymentMethodType> supportedMethods)
    implements ValueObject {

  public GatewayCapabilities {
    supportedMethods = Set.copyOf(supportedMethods);
  }
}
