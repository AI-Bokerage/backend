package io.genfin.api.exception;

import java.util.Map;

public class StateTransitionException extends GenFinException {

  public StateTransitionException(String message, Map<String, Object> metadata) {
    super(CoreErrorCode.ILLEGAL_STATE_TRANSITION, message, null, metadata);
  }
}
