package io.cpms.common.errors;

import org.springframework.http.HttpStatus;

public class ConflictException extends PlatformException {

    public ConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }

    public ConflictException(String code, String message, Object details) {
        super(code, message, HttpStatus.CONFLICT, details);
    }
}
