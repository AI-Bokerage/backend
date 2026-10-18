package com.company.bsmsvc.infrastructure.outbox;

public enum BsmOutboxEventStatus {
    PENDING,
    IN_FLIGHT,
    PUBLISHED,
    FAILED
}
