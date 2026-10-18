package io.cpms.common.errors;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends PlatformException {

    public UnauthorizedException() {
        super("UNAUTHENTICATED", "Authentication required", HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String code, String message) {
        super(code, message, HttpStatus.UNAUTHORIZED);
    }
}
