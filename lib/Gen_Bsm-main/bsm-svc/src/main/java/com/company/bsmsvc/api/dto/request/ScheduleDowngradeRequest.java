package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Schedule subscription downgrade request")
public record ScheduleDowngradeRequest(
    @NotNull
    UUID tenantId,
    @NotNull
    UUID targetPlanVersionId,
    @NotNull
    @Future
    Instant effectiveAt,
    @NotBlank
    String reason,
    @NotNull
    UUID createdBy
) {
}
