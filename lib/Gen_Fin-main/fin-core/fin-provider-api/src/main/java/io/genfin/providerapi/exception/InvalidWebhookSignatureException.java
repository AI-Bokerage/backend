package io.genfin.providerapi.exception;

import io.genfin.api.exception.GenFinException;

public class InvalidWebhookSignatureException extends GenFinException {

  public InvalidWebhookSignatureException(String message) {
    super(ProviderApiErrorCode.INVALID_WEBHOOK_SIGNATURE, message);
  }

  public InvalidWebhookSignatureException(String message, Throwable cause) {
    super(ProviderApiErrorCode.INVALID_WEBHOOK_SIGNATURE, message, cause);
  }
}
