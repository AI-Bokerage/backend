package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.port.UsageLimitsSeedingPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpUsageLimitsSeedingAdapter implements UsageLimitsSeedingPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpUsageLimitsSeedingAdapter.class);

    @Override
    public void seedLimits(UUID tenantId, UUID ppmPlanId) {
        log.debug("Demo: skipping usage limit seeding for tenant={} plan={}", tenantId, ppmPlanId);
    }
}
