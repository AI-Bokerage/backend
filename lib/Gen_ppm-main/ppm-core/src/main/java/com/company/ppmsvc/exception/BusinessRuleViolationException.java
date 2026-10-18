package com.company.ppmsvc.exception;

/**
 * Thrown when an operation violates a domain business rule.
 * Maps to HTTP {@code 400 Bad Request}.
 *
 * @deprecated Prefer {@link BusinessException} with an explicit {@link ErrorCode}.
 */
@Deprecated(since = "foundation", forRemoval = true)
public class BusinessRuleViolationException extends BusinessException {

    public BusinessRuleViolationException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message, cause);
    }
}
