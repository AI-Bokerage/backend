package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.ProrationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Proration preview result for a prospective plan change")
public record ProrationPreviewResponse(

    @Schema(description = "Proration preview ID")
    UUID id,

    @Schema(description = "Subscription the preview belongs to")
    UUID subscriptionId,

    @Schema(description = "Current plan version ID")
    UUID fromPlanVersionId,

    @Schema(description = "Target plan version ID")
    UUID toPlanVersionId,

    @Schema(description = "Proration mode used for calculation")
    ProrationMode prorationMode,

    @Schema(description = "Credit amount from unused portion of current plan (minor currency units)")
    long currentPlanCreditMinor,

    @Schema(description = "Charge for remaining period on target plan (minor currency units)")
    long targetPlanChargeMinor,

    @Schema(description = "Net delta — positive means customer owes more, negative means credit (minor currency units)")
    long delta,

    @Schema(description = "ISO 4217 currency code (e.g. INR)")
    String currency,

    @Schema(description = "Detailed calculation breakdown")
    Map<String, Object> breakdown,

    @Schema(description = "Preview expires at this instant; stale previews should be regenerated")
    Instant expiresAt,

    @Schema(description = "When the preview was calculated")
    Instant createdAt
) {
}
