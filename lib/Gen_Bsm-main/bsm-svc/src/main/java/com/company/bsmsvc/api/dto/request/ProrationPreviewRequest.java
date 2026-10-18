package com.company.bsmsvc.api.dto.request;

import com.company.bsmsvc.domain.enums.ProrationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Request to preview the proration amount for a prospective plan change")
public record ProrationPreviewRequest(

    @Schema(description = "Tenant ID that owns the subscription", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID tenantId,

    @Schema(description = "Target plan version ID to calculate proration against", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID targetPlanVersionId,

    @Schema(description = "Proration mode to apply", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    ProrationMode prorationMode
) {
}
