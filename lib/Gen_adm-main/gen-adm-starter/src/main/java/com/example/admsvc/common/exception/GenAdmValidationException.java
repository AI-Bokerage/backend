package com.example.admsvc.common.exception;

public class GenAdmValidationException extends GenAdmException {

    public GenAdmValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
