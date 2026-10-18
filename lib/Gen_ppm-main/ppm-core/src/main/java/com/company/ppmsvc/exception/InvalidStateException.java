package com.company.ppmsvc.exception;

/**
 * Thrown when a business operation is attempted on an entity in an
 * incompatible state.
 * Maps to HTTP {@code 409 Conflict}.
 */
public class InvalidStateException extends BusinessException {

    public InvalidStateException(String detail) {
        super(ErrorCode.INVALID_STATE_TRANSITION, detail);
    }

    public InvalidStateException(String entityType, String currentState, String requestedAction) {
        super(ErrorCode.INVALID_STATE_TRANSITION,
            "Cannot perform [" + requestedAction + "] on " + entityType
            + " in state [" + currentState + "].");
    }
}
