package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Request to upgrade a subscription to a higher-tier plan version")
public record UpgradeSubscriptionRequest(

    @Schema(description = "Tenant ID that owns this subscription", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID tenantId,

    @Schema(description = "Target plan version ID to upgrade to", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID targetPlanVersionId,

    @Schema(description = "Human-readable reason for the upgrade")
    String reason,

    @Schema(description = "Actor performing the upgrade (email or user ID)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    String performedBy
) {
}
