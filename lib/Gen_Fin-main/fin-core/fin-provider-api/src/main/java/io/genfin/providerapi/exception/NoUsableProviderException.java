package io.genfin.providerapi.exception;

import io.genfin.api.exception.GenFinException;

public class NoUsableProviderException extends GenFinException {

  public NoUsableProviderException(String message) {
    super(ProviderApiErrorCode.PROVIDER_UNAVAILABLE, message);
  }
}
