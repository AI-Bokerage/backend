package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.MigrationPlanStatus;
import java.time.Instant;
import java.util.UUID;

public record MigrationPlanFilter(
    UUID tenantId,
    UUID subscriptionId,
    UUID targetPlanVersionId,
    MigrationPlanStatus status,
    Instant dateFrom,
    Instant dateTo
) {
}
