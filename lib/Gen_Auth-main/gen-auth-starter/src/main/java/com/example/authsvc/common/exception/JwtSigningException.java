package com.example.authsvc.common.exception;

/**
 * Thrown when JWT signing fails, whether via local RSA key or AWS KMS.
 *
 * <p>This is an unchecked runtime exception because signing failure is not
 * recoverable by the caller — it must propagate up to the global exception
 * handler which returns a 500 to the client. The authentication request
 * is NOT partially completed (no token is ever issued).
 */
public class JwtSigningException extends RuntimeException {

    public JwtSigningException(String message) {
        super(message);
    }

    public JwtSigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
