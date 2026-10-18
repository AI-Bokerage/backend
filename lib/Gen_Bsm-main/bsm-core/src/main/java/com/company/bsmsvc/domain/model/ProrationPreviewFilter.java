package com.company.bsmsvc.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ProrationPreviewFilter(
    UUID subscriptionId,
    Instant dateFrom,
    Instant dateTo
) {
}
