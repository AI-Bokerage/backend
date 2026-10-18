package com.example.modauth.config;

import com.example.authsvc.api.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Without this, gen-auth-starter's own {@code GlobalExceptionHandler} (a
 * {@code @RestControllerAdvice} with a catch-all {@code @ExceptionHandler(Exception.class)})
 * swallows every {@link ResponseStatusException} this module throws (invalid
 * invitation link, duplicate account, role-hierarchy violation, deactivated
 * account) into a generic 500 — confirmed by an actual request: inviting an
 * already-registered email returned 500 "An unexpected error occurred"
 * instead of 409. Spring resolves {@code @ExceptionHandler} methods across
 * *all* {@code @RestControllerAdvice} beans by exception-type specificity, not
 * by which advice declared them, so this handler (specific to
 * {@code ResponseStatusException}) wins over that catch-all regardless of
 * bean order.
 */
@Slf4j
@RestControllerAdvice
public class ModAuthExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        log.info("modauth.response_status status={} reason={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(ex.getReason()));
    }
}
