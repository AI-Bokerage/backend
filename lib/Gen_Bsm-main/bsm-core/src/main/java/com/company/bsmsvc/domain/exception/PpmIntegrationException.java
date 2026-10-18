package com.company.bsmsvc.domain.exception;

public class PpmIntegrationException extends RuntimeException {

    public PpmIntegrationException(String message) {
        super(message);
    }

    public PpmIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
