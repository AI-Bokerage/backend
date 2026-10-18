package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.MigrationAction;
import com.company.bsmsvc.domain.enums.MigrationResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "A single item within a migration plan")
public record MigrationPlanItemResponse(

    @Schema(description = "Item ID")
    UUID id,

    @Schema(description = "Migration plan this item belongs to")
    UUID migrationPlanId,

    @Schema(description = "Type of resource affected")
    MigrationResourceType resourceType,

    @Schema(description = "ID of the specific resource")
    UUID resourceId,

    @Schema(description = "Action to apply to this resource")
    MigrationAction action,

    @Schema(description = "Optional metadata key-value pairs")
    Map<String, Object> metadata,

    @Schema(description = "When this item was created")
    Instant createdAt
) {
}
