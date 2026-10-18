package com.company.bsmsvc.api.dto.request;

import com.company.bsmsvc.domain.enums.MigrationAction;
import com.company.bsmsvc.domain.enums.MigrationResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

@Schema(description = "A single resource item within a migration plan")
public record CreateMigrationPlanItemRequest(

    @Schema(description = "Resource type affected by this migration item", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    MigrationResourceType resourceType,

    @Schema(description = "ID of the specific resource", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID resourceId,

    @Schema(description = "Action to apply to this resource on migration", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    MigrationAction action,

    @Schema(description = "Optional metadata key-value pairs for this item")
    Map<String, Object> metadata
) {
}
