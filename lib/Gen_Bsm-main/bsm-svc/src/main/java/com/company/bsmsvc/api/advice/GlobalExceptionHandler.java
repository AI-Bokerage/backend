package com.company.bsmsvc.api.advice;

import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.domain.exception.AddOnAlreadyPurchasedException;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.exception.SubscriptionAddOnNotFoundException;
import com.company.bsmsvc.domain.exception.UsageDataUnavailableException;
import com.company.bsmsvc.domain.exception.CreditNoteNotFoundException;
import com.company.bsmsvc.domain.exception.InvoiceNotFoundException;
import com.company.bsmsvc.domain.exception.MigrationPlanNotFoundException;
import com.company.bsmsvc.domain.exception.PlanNotFoundException;
import com.company.bsmsvc.domain.exception.ProrationPreviewNotFoundException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.exception.PaymentGatewayException;
import com.company.bsmsvc.domain.exception.PaymentMethodNotFoundException;
import com.company.bsmsvc.domain.exception.PaymentNotFoundException;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.exception.WebhookVerificationException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("[403 ACCESS_DENIED] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to access this resource.", request.getRequestURI());
    }

    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handlePlanNotFound(PlanNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 PLAN_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleSubscriptionNotFound(SubscriptionNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 SUBSCRIPTION_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(SubscriptionAddOnNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleSubscriptionAddOnNotFound(SubscriptionAddOnNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 SUBSCRIPTION_ADD_ON_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "SUBSCRIPTION_ADD_ON_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AddOnAlreadyPurchasedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAddOnAlreadyPurchased(AddOnAlreadyPurchasedException ex, HttpServletRequest request) {
        log.warn("[409 ADD_ON_ALREADY_PURCHASED] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "ADD_ON_ALREADY_PURCHASED", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvoiceNotFound(InvoiceNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 INVOICE_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ProrationPreviewNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleProrationPreviewNotFound(ProrationPreviewNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 PRORATION_PREVIEW_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "PRORATION_PREVIEW_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MigrationPlanNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMigrationPlanNotFound(MigrationPlanNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 MIGRATION_PLAN_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "MIGRATION_PLAN_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(CreditNoteNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleCreditNoteNotFound(CreditNoteNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 CREDIT_NOTE_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "CREDIT_NOTE_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handlePaymentNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 PAYMENT_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(WebhookVerificationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleWebhookVerification(WebhookVerificationException ex, HttpServletRequest request) {
        log.warn("[400 WEBHOOK_VERIFICATION_FAILED] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "WEBHOOK_VERIFICATION_FAILED", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(PaymentMethodNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handlePaymentMethodNotFound(PaymentMethodNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 PAYMENT_METHOD_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "PAYMENT_METHOD_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handlePaymentGatewayError(PaymentGatewayException ex, HttpServletRequest request) {
        log.error("[502 PAYMENT_GATEWAY_ERROR] method={} path={}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(PpmIntegrationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handlePpmIntegration(PpmIntegrationException ex, HttpServletRequest request) {
        log.error("[502 PPM_INTEGRATION_ERROR] method={} path={}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.BAD_GATEWAY, "PPM_INTEGRATION_ERROR", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(UsageDataUnavailableException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleUsageDataUnavailable(
            UsageDataUnavailableException ex, HttpServletRequest request) {
        log.warn("[503 USAGE_VALIDATION_UNAVAILABLE] method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "USAGE_VALIDATION_UNAVAILABLE",
            "Usage validation is temporarily unavailable. Please try again shortly.", request.getRequestURI());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleUnsupportedOperation(UnsupportedOperationException ex, HttpServletRequest request) {
        log.warn("[501 NOT_IMPLEMENTED] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(TenantBillingProfileNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTenantBillingProfileNotFound(TenantBillingProfileNotFoundException ex, HttpServletRequest request) {
        log.warn("[404 BILLING_PROFILE_NOT_FOUND] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "BILLING_PROFILE_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleBusinessRuleViolation(BusinessRuleViolationException ex, HttpServletRequest request) {
        log.warn("[409 BUSINESS_RULE_VIOLATION] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<ErrorResponse>> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        log.warn("[409 OPTIMISTIC_LOCK_CONFLICT] method={} path={} message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "Resource was modified concurrently. Please retry.", request.getRequestURI());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<ErrorResponse>> handleValidation(Exception ex, HttpServletRequest request) {
        String message = "Validation failed";
        if (ex instanceof MethodArgumentNotValidException methodEx && !methodEx.getBindingResult().getFieldErrors().isEmpty()) {
            FieldError fieldError = methodEx.getBindingResult().getFieldErrors().getFirst();
            message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        log.warn("[400 VALIDATION_ERROR] method={} path={} message={}", request.getMethod(), request.getRequestURI(), message);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String message = "Malformed or unreadable request body";
        Throwable cause = ex.getCause();
        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife
            && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            Object invalid = ife.getValue();
            String accepted = java.util.Arrays.stream(ife.getTargetType().getEnumConstants())
                .map(Object::toString)
                .collect(java.util.stream.Collectors.joining(", "));
            message = "Invalid value '" + invalid + "' for field '" + fieldNameFrom(ife) + "'. Accepted values: [" + accepted + "]";
        }
        log.warn("[400 INVALID_REQUEST_BODY] method={} path={} message={}", request.getMethod(), request.getRequestURI(), message);
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", message, request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        if (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) {
            String accepted = java.util.Arrays.stream(ex.getRequiredType().getEnumConstants())
                .map(Object::toString)
                .collect(java.util.stream.Collectors.joining(", "));
            message += ". Accepted values: [" + accepted + "]";
        }
        log.warn("[400 INVALID_PARAMETER] method={} path={} message={}", request.getMethod(), request.getRequestURI(), message);
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, request.getRequestURI());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        log.warn("[400 MISSING_PARAMETER] method={} path={} message={}", request.getMethod(), request.getRequestURI(), message);
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", message, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("[500 INTERNAL_ERROR] method={} path={}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error occurred", request.getRequestURI());
    }

    private ResponseEntity<ApiResponse<ErrorResponse>> buildResponse(
        HttpStatus status,
        String code,
        String message,
        String path
    ) {
        ErrorResponse error = new ErrorResponse(Instant.now(), code, message, path);
        return ResponseEntity.status(status).body(ApiResponse.error(message, error));
    }

    private String fieldNameFrom(com.fasterxml.jackson.databind.exc.InvalidFormatException ex) {
        if (ex.getPath() != null && !ex.getPath().isEmpty()) {
            return ex.getPath().stream()
                .map(com.fasterxml.jackson.databind.JsonMappingException.Reference::getFieldName)
                .filter(f -> f != null)
                .collect(java.util.stream.Collectors.joining("."));
        }
        return "unknown";
    }
}
