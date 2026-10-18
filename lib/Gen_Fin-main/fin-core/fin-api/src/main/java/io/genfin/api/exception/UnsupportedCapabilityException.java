package io.genfin.api.exception;

/** Signals a feature or capability the current implementation/provider does not support. */
public class UnsupportedCapabilityException extends GenFinException {

  public UnsupportedCapabilityException(String message) {
    super(CoreErrorCode.UNKNOWN, message);
  }
}
