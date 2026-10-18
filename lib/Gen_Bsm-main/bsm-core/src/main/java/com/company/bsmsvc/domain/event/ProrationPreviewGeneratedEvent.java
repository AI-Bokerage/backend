package com.company.bsmsvc.domain.event;

import com.company.bsmsvc.domain.enums.ProrationMode;
import java.time.Instant;
import java.util.UUID;

public record ProrationPreviewGeneratedEvent(
    UUID previewId,
    UUID subscriptionId,
    UUID tenantId,
    UUID fromPlanVersionId,
    UUID toPlanVersionId,
    ProrationMode prorationMode,
    long proratedAmountMinor,
    Instant occurredAt
) {
}
