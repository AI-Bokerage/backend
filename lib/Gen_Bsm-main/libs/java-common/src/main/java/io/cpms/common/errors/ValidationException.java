package io.cpms.common.errors;

import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;

public class ValidationException extends PlatformException {

    public record FieldError(String field, String message) {}

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String message, List<FieldError> fieldErrors) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST,
              Map.of("fieldErrors", fieldErrors));
    }

    public static ValidationException of(String field, String message) {
        return new ValidationException("Validation failed", List.of(new FieldError(field, message)));
    }
}
