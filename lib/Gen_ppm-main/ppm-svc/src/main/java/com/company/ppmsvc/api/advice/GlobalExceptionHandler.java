package com.company.ppmsvc.api.advice;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.ErrorResponse;
import com.company.ppmsvc.api.dto.ErrorResponse.FieldViolation;
import com.company.ppmsvc.exception.AccessDeniedException;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.InvalidStateException;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Central exception handler for all PPM controller endpoints.
 *
 * <p>Handler precedence (most-specific first):
 * <ol>
 *   <li>{@link ResourceNotFoundException} → 404</li>
 *   <li>{@link AccessDeniedException} (PPM domain) → 403</li>
 *   <li>{@link org.springframework.security.access.AccessDeniedException} (Spring) → 403</li>
 *   <li>{@link InvalidStateException} → 409</li>
 *   <li>{@link OptimisticLockingFailureException} → 409</li>
 *   <li>{@link DataIntegrityViolationException} → 409</li>
 *   <li>{@link BusinessException} (base) → 400</li>
 *   <li>{@link MethodArgumentNotValidException} → 422</li>
 *   <li>{@link Exception} (catch-all) → 500</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleNotFound(
        ResourceNotFoundException ex, HttpServletRequest req
    ) {
        log.warn("[404] {} path={}", ex.getErrorCode().getCode(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleDomainAccessDenied(
        AccessDeniedException ex, HttpServletRequest req
    ) {
        log.warn("[403] {} path={}", ex.getErrorCode().getCode(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleSpringAccessDenied(
        org.springframework.security.access.AccessDeniedException ex, HttpServletRequest req
    ) {
        log.warn("[403 Spring Security] path={}", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(ErrorCode.ACCESS_DENIED.getDefaultMessage()));
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleDataIntegrity(
        DataIntegrityViolationException ex, HttpServletRequest req
    ) {
        String combined = buildConstraintMessage(ex);
        ErrorCode code = resolveConstraintCode(combined);

        log.warn("[409 CONSTRAINT] {} path={} constraint={}",
            code.getCode(), req.getRequestURI(),
            combined.substring(0, Math.min(combined.length(), 200)));
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(code.getDefaultMessage()));
    }

    private static ErrorCode resolveConstraintCode(String constraintMessage) {
        if (constraintMessage.contains("uq_ppm_modules_code")) {
            return ErrorCode.MODULE_CODE_ALREADY_EXISTS;
        }
        if (constraintMessage.contains("uq_ppm_plans_code")) {
            return ErrorCode.PLAN_CODE_ALREADY_EXISTS;
        }
        if (constraintMessage.contains("uq_ppm_plans_slug")) {
            return ErrorCode.PLAN_SLUG_ALREADY_EXISTS;
        }
        if (constraintMessage.contains("uq_ppm_plan_modules_plan_module")) {
            return ErrorCode.MODULE_ALREADY_ASSIGNED_TO_PLAN;
        }
        if (constraintMessage.contains("uq_ppm_entitlements_code")) {
            return ErrorCode.ENTITLEMENT_CODE_ALREADY_EXISTS;
        }
        if (constraintMessage.contains("uq_ppm_add_ons_code")) {
            return ErrorCode.ADD_ON_CODE_ALREADY_EXISTS;
        }
        if (constraintMessage.contains("uq_ppm_add_on_prices_active")) {
            return ErrorCode.ADD_ON_PRICE_ALREADY_EXISTS;
        }
        if (constraintMessage.contains("uq_ppm_plan_add_on")) {
            return ErrorCode.PLAN_ADD_ON_ALREADY_ASSIGNED;
        }
        return ErrorCode.DUPLICATE_RESOURCE;
    }

    private static String buildConstraintMessage(DataIntegrityViolationException ex) {
        StringBuilder sb = new StringBuilder();
        if (ex.getMessage() != null) sb.append(ex.getMessage());
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause.getMessage() != null) sb.append(' ').append(cause.getMessage());
            cause = cause.getCause();
        }
        return sb.toString().toLowerCase();
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidState(
        InvalidStateException ex, HttpServletRequest req
    ) {
        log.warn("[409 INVALID_STATE] {} path={}", ex.getErrorCode().getCode(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleOptimisticLock(
        OptimisticLockingFailureException ex, HttpServletRequest req
    ) {
        log.warn("[409 OPTIMISTIC_LOCK] path={} message={}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.getDefaultMessage()));
    }

    // ── 400 / 409 Bad Request / Conflict ─────────────────────────────────────

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleBusiness(
        BusinessException ex, HttpServletRequest req
    ) {
        boolean isConflict = ex.getErrorCode() == ErrorCode.MODULE_CODE_ALREADY_EXISTS
            || ex.getErrorCode() == ErrorCode.PLAN_CODE_ALREADY_EXISTS
            || ex.getErrorCode() == ErrorCode.MODULE_ALREADY_ASSIGNED_TO_PLAN
            || ex.getErrorCode() == ErrorCode.ENTITLEMENT_CODE_ALREADY_EXISTS
            || ex.getErrorCode() == ErrorCode.PLAN_ENTITLEMENT_ALREADY_ASSIGNED
            || ex.getErrorCode() == ErrorCode.PLAN_PRICE_ALREADY_EXISTS
            || ex.getErrorCode() == ErrorCode.PLAN_VERSION_ALREADY_EXISTS
            || ex.getErrorCode() == ErrorCode.PLAN_VERSION_DATE_CONFLICT
            || ex.getErrorCode() == ErrorCode.PROMO_CODE_ALREADY_EXISTS
            || ex.getErrorCode() == ErrorCode.PROMO_CODE_PLAN_ALREADY_ASSIGNED
            || ex.getErrorCode() == ErrorCode.ADD_ON_CODE_ALREADY_EXISTS
            || ex.getErrorCode() == ErrorCode.PLAN_ADD_ON_ALREADY_ASSIGNED
            || ex.getErrorCode() == ErrorCode.COUPON_CODE_ALREADY_EXISTS
            || ex.getErrorCode() == ErrorCode.REFERRAL_CODE_ALREADY_EXISTS
            || ex.getErrorCode() == ErrorCode.REFERRAL_ALREADY_CONVERTED
            || ex.getErrorCode() == ErrorCode.REFERRAL_CAP_REACHED;
        boolean isValidationError = ex.getErrorCode() == ErrorCode.VALIDATION_ERROR;
        HttpStatus status = isConflict        ? HttpStatus.CONFLICT
                          : isValidationError ? HttpStatus.UNPROCESSABLE_CONTENT
                          :                    HttpStatus.BAD_REQUEST;
        log.warn("[{} BUSINESS] {} path={} message={}", status.value(),
            ex.getErrorCode().getCode(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(status)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleNotReadable(
        HttpMessageNotReadableException ex, HttpServletRequest req
    ) {
        String detail = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        log.warn("[400 MALFORMED_JSON] path={} detail={}", req.getRequestURI(), detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Malformed or incomplete request body. " +
                "Ensure the JSON is well-formed and all required fields are present."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex, HttpServletRequest req
    ) {
        String msg = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s.",
            ex.getValue(), ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("[400 TYPE_MISMATCH] path={} message={}", req.getRequestURI(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(msg));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMissingParam(
        MissingServletRequestParameterException ex, HttpServletRequest req
    ) {
        String msg = String.format("Required query parameter '%s' of type '%s' is missing.",
            ex.getParameterName(), ex.getParameterType());
        log.warn("[400 MISSING_PARAM] path={} message={}", req.getRequestURI(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(msg));
    }

    // ── 405 Method Not Allowed ────────────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException ex, HttpServletRequest req
    ) {
        log.warn("[405] method={} path={}", ex.getMethod(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ApiResponse.error(
                String.format("HTTP method '%s' is not supported for this endpoint.", ex.getMethod())));
    }

    // ── 422 Unprocessable Entity ──────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleValidation(
        MethodArgumentNotValidException ex, HttpServletRequest req
    ) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage()))
            .toList();

        String summary = violations.isEmpty() ? "Validation failed"
            : violations.get(0).message();

        log.warn("[422 VALIDATION] path={} violations={}", req.getRequestURI(), violations.size());

        ErrorResponse errorResponse = new ErrorResponse(
            Instant.now(),
            ErrorCode.VALIDATION_ERROR.getCode(),
            summary,
            req.getRequestURI(),
            violations
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
            .body(ApiResponse.errorWithData(summary, errorResponse));
    }

    // ── Client disconnect ─────────────────────────────────────────────────────

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException ex, HttpServletRequest req) {
        log.debug("[client-disconnect] path={} reason={}", req.getRequestURI(), ex.getMessage());
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleGeneric(
        Exception ex, HttpServletRequest req
    ) {
        log.error("[500 INTERNAL] path={} message={}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()));
    }
}
