package io.genfin.payment.exception;

import io.genfin.api.exception.GenFinException;

public class GatewayIntegrationException extends GenFinException {

  public GatewayIntegrationException(String message, Throwable cause) {
    super(PaymentErrorCode.GATEWAY_INTEGRATION_FAILED, message, cause);
  }
}
