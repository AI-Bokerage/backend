package com.company.ppmsvc.exception;

import lombok.Getter;

/**
 * Base class for all PPM domain exceptions.
 *
 * <p>Every exception raised by domain or application code should extend this
 * class and supply an {@link ErrorCode}.  The {@code GlobalExceptionHandler}
 * maps subclasses to HTTP status codes by type — not by code — so subclasses
 * must be meaningful types.
 *
 * <p>No framework imports — safe to use anywhere in the domain layer.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
    }
}
