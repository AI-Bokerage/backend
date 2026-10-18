package com.company.bsmsvc.domain.port;

import java.util.UUID;

/** Best-effort synchronous seeding of usage quota limits right after a subscription is created. */
public interface UsageLimitsSeedingPort {
    void seedLimits(UUID tenantId, UUID ppmPlanId);
}
