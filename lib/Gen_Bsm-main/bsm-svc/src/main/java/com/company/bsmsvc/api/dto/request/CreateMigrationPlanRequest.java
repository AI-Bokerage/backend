package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Schema(description = "Request to create a new migration plan for a subscription")
public record CreateMigrationPlanRequest(

    @Schema(description = "Subscription to create the migration plan for", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID subscriptionId,

    @Schema(description = "Tenant that owns the subscription", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID tenantId,

    @Schema(description = "Target plan version to migrate to", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID targetPlanVersionId,

    @Schema(description = "Actor creating this migration plan", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID createdBy,

    @Schema(description = "List of resource items in this migration plan", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    List<CreateMigrationPlanItemRequest> items
) {
}
