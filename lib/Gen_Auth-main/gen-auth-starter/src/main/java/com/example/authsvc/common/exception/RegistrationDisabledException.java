package com.example.authsvc.common.exception;

public class RegistrationDisabledException extends RuntimeException {
    public RegistrationDisabledException(String message) {
        super(message);
    }
}
