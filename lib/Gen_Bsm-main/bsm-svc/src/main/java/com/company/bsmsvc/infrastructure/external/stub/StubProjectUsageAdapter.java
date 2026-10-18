package com.company.bsmsvc.infrastructure.external.stub;

import com.company.bsmsvc.domain.port.ProjectUsagePort;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub project usage adapter — active in ALL profiles including production.
 *
 * <p><strong>Technical debt:</strong> no service in the current platform writes
 * {@code tenant_quotas.quota_used} for {@code PROJECTS}. Evidence from Phase 4
 * audit: the only writers are the provisioning bootstrap (sets 0) and a manual
 * super-admin PATCH on TNT-SVC. No automated writer exists anywhere in the platform.
 *
 * <p>Returns {@code 0} — the safe conservative default. A count of 0 means the
 * downgrade preflight will never generate false-positive {@code PROJECTS_OVER_LIMIT}
 * warnings. Returning 22 (the previous hardcoded value) was worse: it generated
 * spurious warnings for any plan with {@code maxActiveProjects < 22}.
 *
 * <p>To replace this stub, implement a project-management service that increments
 * {@code tenant_quotas.quota_used} for {@code PROJECTS} and expose it via a
 * queryable internal API, then wire that API into a real {@code ProjectUsagePort}
 * adapter.</p>
 */
@Slf4j
@Component
public class StubProjectUsageAdapter implements ProjectUsagePort {

    @Override
    public int getActiveProjectCount(UUID tenantId) {
        log.debug("[StubProjectUsageAdapter] tenantId={} returning 0 (no authoritative project-count source in platform)",
            tenantId);
        return 0;
    }
}
