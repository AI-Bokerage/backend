package com.company.bsmsvc.domain.exception;

/**
 * Thrown when usage data cannot be retrieved from an external service
 * (e.g., ADM-SVC is unreachable or returns an unexpected error).
 *
 * <p>In the downgrade preflight context this exception is intentionally
 * fail-closed: the preflight operation is blocked and the caller receives
 * HTTP 503 with code {@code USAGE_VALIDATION_UNAVAILABLE}. This is safer
 * than returning zero usage and allowing a potentially unsafe downgrade.</p>
 */
public class UsageDataUnavailableException extends RuntimeException {

    public UsageDataUnavailableException(String service, String reason) {
        super("Usage data unavailable from " + service + ": " + reason);
    }

    public UsageDataUnavailableException(String service, Throwable cause) {
        super("Usage data unavailable from " + service + ": " + cause.getMessage(), cause);
    }
}
