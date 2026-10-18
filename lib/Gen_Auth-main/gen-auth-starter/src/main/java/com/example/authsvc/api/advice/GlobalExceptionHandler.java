package com.example.authsvc.api.advice;

import com.example.authsvc.api.dto.response.ErrorResponse;
import com.example.authsvc.common.exception.AccountLockedException;
import com.example.authsvc.common.exception.ActiveKeyRetirementException;
import com.example.authsvc.common.exception.BadRequestException;
import com.example.authsvc.common.exception.EmailAlreadyExistsException;
import com.example.authsvc.common.exception.InvalidCredentialsException;
import com.example.authsvc.common.exception.KeyNotFoundException;
import com.example.authsvc.common.exception.MagicLinkInvalidException;
import com.example.authsvc.common.exception.RateLimitExceededException;
import com.example.authsvc.common.exception.RegistrationDisabledException;
import com.example.authsvc.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
                                                                   HttpServletRequest request) {
        log.warn("auth.invalid_credentials path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Invalid credentials"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex,
                                                             HttpServletRequest request) {
        log.warn("auth.unauthorized path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                           HttpServletRequest request) {
        log.warn("auth.bad_request path={} message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex,
                                                              HttpServletRequest request) {
        log.warn("auth.account_locked path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("Account temporarily locked. Please try again later."));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex,
                                                          HttpServletRequest request) {
        log.warn("rate_limit.exceeded path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex,
                                                                    HttpServletRequest request) {
        log.info("auth.email_already_exists path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MagicLinkInvalidException.class)
    public ResponseEntity<ErrorResponse> handleMagicLinkInvalid(MagicLinkInvalidException ex,
                                                                 HttpServletRequest request) {
        log.info("magic_link.invalid path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RegistrationDisabledException.class)
    public ResponseEntity<ErrorResponse> handleRegistrationDisabled(RegistrationDisabledException ex,
                                                                      HttpServletRequest request) {
        log.info("auth.registration_disabled path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(KeyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleKeyNotFound(KeyNotFoundException ex,
                                                            HttpServletRequest request) {
        log.info("jwks.key_not_found path={} message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ActiveKeyRetirementException.class)
    public ResponseEntity<ErrorResponse> handleActiveKeyRetirement(ActiveKeyRetirementException ex,
                                                                    HttpServletRequest request) {
        log.info("jwks.active_key_retirement_rejected path={} message={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        // Field-level errors first (e.g. @NotBlank, @Size)
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                // Fall back to global errors (e.g. @PasswordsMatch class-level constraint)
                .orElseGet(() -> ex.getBindingResult().getGlobalErrors().stream()
                        .findFirst()
                        .map(err -> err.getDefaultMessage())
                        .orElse("Validation failed"));
        log.debug("auth.validation_failed path={} message={}", request.getRequestURI(), message);
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex,
                                                           HttpServletRequest request) {
        log.error("error.unexpected path={} reason={}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("An unexpected error occurred"));
    }
}
