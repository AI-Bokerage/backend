package com.example.admsvc.common.exception;

public class GenAdmForbiddenException extends GenAdmException {

    public GenAdmForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
