package com.company.ppmsvc.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Structured error payload returned inside {@link ApiResponse} when an
 * operation fails.
 *
 * <p>The {@code code} field carries a machine-readable {@code PPM-xxxx} string
 * that clients can use for programmatic error handling.  The {@code violations}
 * list is non-null only for validation errors (HTTP 422).
 */
@Schema(description = "Structured error detail")
public record ErrorResponse(
    @Schema(description = "Time the error occurred") Instant timestamp,
    @Schema(description = "Machine-readable error code") String code,
    @Schema(description = "Human-readable error message") String message,
    @Schema(description = "Request path that produced the error") String path,
    @Schema(description = "Field-level violations for validation errors") List<FieldViolation> violations
) {

    @Schema(description = "A single field-level validation violation")
    public record FieldViolation(
        @Schema(description = "Field name") String field,
        @Schema(description = "Violation message") String message
    ) {}
}
