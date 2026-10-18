package com.company.bsmsvc.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable fact: this tenant has consumed their one lifetime free trial.
 * Written once when the first TRIALING subscription is created; never updated or deleted.
 */
@Getter
@Builder
public class TenantTrialRecord {

    private final UUID tenantId;
    private final UUID subscriptionId;
    private final Instant trialConsumedAt;
}
