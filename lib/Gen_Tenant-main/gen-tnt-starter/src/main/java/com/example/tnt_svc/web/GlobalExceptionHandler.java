// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/GlobalExceptionHandler.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.domain.exception.DuplicateSlugException;
import com.example.tnt_svc.domain.exception.InvalidTenantStateTransitionException;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotFoundException;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotRetryableException;
import com.example.tnt_svc.domain.exception.ProvisioningLockException;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.web.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateSlugException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSlug(DuplicateSlugException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("duplicate_slug", e.getMessage()));
    }

    @ExceptionHandler(InvalidTenantStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidTenantStateTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("invalid_state_transition", e.getMessage()));
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFound(TenantNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("tenant_not_found", e.getMessage()));
    }

    @ExceptionHandler(ProvisioningJobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobNotFound(ProvisioningJobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("job_not_found", e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        // Concurrent inserts racing on a unique constraint (slug / idempotency key) land here.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("conflict", "Resource already exists or conflicts with an existing record"));
    }

    @ExceptionHandler(ProvisioningLockException.class)
    public ResponseEntity<ErrorResponse> handleProvisioningLock(ProvisioningLockException e) {
        // Callback lost a race for the per-tenant lock; signal the sender to retry (at-least-once delivery).
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("lock_contention", e.getMessage()));
    }

    @ExceptionHandler(ProvisioningJobNotRetryableException.class)
    public ResponseEntity<ErrorResponse> handleJobNotRetryable(ProvisioningJobNotRetryableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("job_not_retryable", e.getMessage()));
    }
}
