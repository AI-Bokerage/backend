package com.example.authsvc.common.exception;

/** Thrown when a client has exceeded the allowed rate limit for a given operation. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
