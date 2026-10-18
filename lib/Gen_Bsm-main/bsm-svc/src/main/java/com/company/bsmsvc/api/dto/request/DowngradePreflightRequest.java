package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Request to run downgrade preflight analysis for a prospective plan downgrade")
public record DowngradePreflightRequest(

    @Schema(description = "Tenant ID that owns the subscription", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID tenantId,

    @Schema(description = "Target (lower-tier) plan version ID to check against", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID targetPlanVersionId
) {
}
