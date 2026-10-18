package io.genfin.api.exception;

public class ConfigurationException extends GenFinException {

  public ConfigurationException(String message) {
    super(CoreErrorCode.CONFIGURATION_INVALID, message);
  }

  public ConfigurationException(String message, Throwable cause) {
    super(CoreErrorCode.CONFIGURATION_INVALID, message, cause);
  }
}
