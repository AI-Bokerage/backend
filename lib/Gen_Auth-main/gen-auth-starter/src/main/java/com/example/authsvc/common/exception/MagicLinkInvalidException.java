package com.example.authsvc.common.exception;

/** Thrown when a magic-link token is missing, invalid, or expired. */
public class MagicLinkInvalidException extends RuntimeException {
    public MagicLinkInvalidException() {
        super("Invalid or expired reset link");
    }
}
