package com.company.bsmsvc.domain.exception;

public class WebhookVerificationException extends RuntimeException {
    public WebhookVerificationException(String message) {
        super(message);
    }
    public WebhookVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
