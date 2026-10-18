package com.example.admsvc.api.advice;

import com.example.admsvc.api.dto.response.ApiErrorResponse;
import com.example.admsvc.common.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.admsvc.api.controller")
public class GlobalExceptionHandler {

    @ExceptionHandler(GenAdmNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(GenAdmNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(GenAdmConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(GenAdmConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(GenAdmValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(GenAdmValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(GenAdmForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(GenAdmForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(GenAdmConfigException.class)
    public ResponseEntity<ApiErrorResponse> handleConfig(GenAdmConfigException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }
}
