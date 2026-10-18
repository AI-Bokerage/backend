package com.example.admsvc.common.exception;

public abstract class GenAdmException extends RuntimeException {

    private final String code;

    protected GenAdmException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
