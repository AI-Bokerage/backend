package com.example.admsvc.common.exception;

public class GenAdmConflictException extends GenAdmException {

    public GenAdmConflictException(String message) {
        super("CONFLICT", message);
    }
}
