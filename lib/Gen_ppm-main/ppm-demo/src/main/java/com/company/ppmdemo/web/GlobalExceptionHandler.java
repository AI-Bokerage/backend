package com.company.ppmdemo.web;

import com.company.ppmsvc.exception.AccessDeniedException;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.InvalidStateException;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps ppm-core domain exception *types* to HTTP status — exactly the pattern
 * INTEGRATION_GUIDE.md describes. Every exception ppm-core throws extends
 * {@link BusinessException} and carries an {@code ErrorCode}; this is the
 * host's decision about how to represent that on the wire, not ppm-core's.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(ex));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(ex));
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidState(InvalidStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(ex));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(ex));
    }

    private Map<String, String> body(BusinessException ex) {
        return Map.of("code", ex.getErrorCode().getCode(), "message", ex.getMessage());
    }
}
