package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.MigrationPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Migration plan details")
public record MigrationPlanResponse(

    @Schema(description = "Migration plan ID")
    UUID id,

    @Schema(description = "Subscription this plan is for")
    UUID subscriptionId,

    @Schema(description = "Tenant owning the subscription")
    UUID tenantId,

    @Schema(description = "Target plan version to migrate to")
    UUID targetPlanVersionId,

    @Schema(description = "Current status of the migration plan")
    MigrationPlanStatus status,

    @Schema(description = "Actor who created this migration plan")
    UUID createdBy,

    @Schema(description = "Resource items included in this migration plan")
    List<MigrationPlanItemResponse> items,

    @Schema(description = "When the migration plan was created")
    Instant createdAt,

    @Schema(description = "When the migration plan was last updated")
    Instant updatedAt
) {
}
