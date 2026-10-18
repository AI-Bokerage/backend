package com.example.admsvc.common.exception;

public class GenAdmNotFoundException extends GenAdmException {

    public GenAdmNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
