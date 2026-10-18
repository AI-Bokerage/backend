package io.cpms.common.errors;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends PlatformException {

    public BusinessRuleException(String code, String message) {
        super(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessRuleException(String code, String message, Object details) {
        super(code, message, HttpStatus.UNPROCESSABLE_ENTITY, details);
    }
}
