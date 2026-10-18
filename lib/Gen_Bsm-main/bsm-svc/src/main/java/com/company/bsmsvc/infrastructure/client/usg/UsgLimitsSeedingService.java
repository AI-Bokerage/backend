package com.company.bsmsvc.infrastructure.client.usg;

import com.company.bsmsvc.domain.port.UsageLimitsSeedingPort;
import com.company.bsmsvc.infrastructure.client.auth.AuthServiceTokenClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Synchronously seeds USG-SVC's Valkey quota limits right after a subscription
 * is created, so a lost/delayed {@code bsm.subscription.created} RabbitMQ
 * message no longer leaves a tenant stuck at fail-open ("-1"/unlimited)
 * indefinitely.
 *
 * <p>Never throws — this is a best-effort accelerator, not a hard dependency.
 * The existing async event (still published unconditionally) remains the
 * safety net if this call, or the service-token fetch it depends on, fails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsgLimitsSeedingService implements UsageLimitsSeedingPort {

    private final AuthServiceTokenClient authServiceTokenClient;
    private final UsgLimitsClient         usgLimitsClient;

    public void seedLimits(UUID tenantId, UUID ppmPlanId) {
        if (tenantId == null || ppmPlanId == null) {
            return;
        }

        String token = authServiceTokenClient.requestServiceToken();
        if (token == null) {
            log.warn("[UsgLimitsSeedingService] no service token — skipping synchronous seed, "
                + "relying on async bsm.subscription.created tenantId={}", tenantId);
            return;
        }

        usgLimitsClient.seedLimits(tenantId, ppmPlanId, token);
    }
}
