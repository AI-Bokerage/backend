package io.cpms.common.errors;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends PlatformException {

    public ForbiddenException() {
        super("PERMISSION_DENIED", "Access denied", HttpStatus.FORBIDDEN);
    }

    public ForbiddenException(String code, String message) {
        super(code, message, HttpStatus.FORBIDDEN);
    }
}
