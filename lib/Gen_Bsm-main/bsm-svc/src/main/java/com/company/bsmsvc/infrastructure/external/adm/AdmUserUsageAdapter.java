package com.company.bsmsvc.infrastructure.external.adm;

import com.company.bsmsvc.domain.model.UserUsageCounts;
import com.company.bsmsvc.domain.port.UserUsagePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Real {@link UserUsagePort} implementation backed by ADM-SVC.
 *
 * <p>A single HTTP call to {@code /internal/tenants/{id}/usage-metrics} returns both
 * active internal and client user counts in one response. All callers use
 * {@link #getUserUsageCounts} to obtain both values without a second round-trip.</p>
 *
 * <p><strong>Fail-closed contract:</strong> if ADM-SVC is unreachable or returns an error,
 * {@code UsageDataUnavailableException} propagates to the caller. For downgrade preflight
 * this results in HTTP 503 — safer than returning 0 and allowing an unsafe downgrade.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdmUserUsageAdapter implements UserUsagePort {

    private final AdmSvcClient admSvcClient;

    @Override
    public UserUsageCounts getUserUsageCounts(UUID tenantId) {
        AdmUsageMetricsResponse metrics = admSvcClient.getUsageMetrics(tenantId);
        int internal = (int) Math.min(metrics.activeInternalUsers(), Integer.MAX_VALUE);
        int client   = (int) Math.min(metrics.activeClientUsers(),   Integer.MAX_VALUE);
        log.debug("[AdmUserUsageAdapter] tenantId={} activeInternalUsers={} activeClientUsers={}",
            tenantId, internal, client);
        return new UserUsageCounts(internal, client);
    }
}
