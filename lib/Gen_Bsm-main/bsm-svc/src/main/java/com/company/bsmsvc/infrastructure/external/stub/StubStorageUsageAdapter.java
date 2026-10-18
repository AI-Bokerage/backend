package com.company.bsmsvc.infrastructure.external.stub;

import com.company.bsmsvc.domain.port.StorageUsagePort;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub storage usage adapter — active in ALL profiles including production.
 *
 * <p><strong>Technical debt:</strong> no service in the current platform maintains
 * an authoritative per-tenant storage byte count. Evidence from Phase 4 audit:
 * {@code tenant_quotas.quota_used} for {@code STORAGE_MB} is set to 0 at provisioning
 * and only updated via a manual super-admin PATCH. TBR-SVC tracks branding asset sizes
 * but those are capped at 10 MB per asset and represent a negligible fraction of
 * total tenant storage. No general storage accounting service exists.
 *
 * <p>Returns {@code 0L} — the safe conservative default. A count of 0 means the
 * downgrade preflight will never generate false-positive {@code STORAGE_LIMIT_EXCEEDED}
 * warnings. Returning 2 GB (the previous hardcoded value) was worse: it generated
 * spurious warnings for any plan with {@code storageQuotaBytes < 2 GB}.
 *
 * <p>To replace this stub, implement a storage aggregation service that collects
 * per-tenant S3 object metadata and exposes it via an internal API, then wire
 * that into a real {@code StorageUsagePort} adapter.</p>
 */
@Slf4j
@Component
public class StubStorageUsageAdapter implements StorageUsagePort {

    @Override
    public long getUsedStorageBytes(UUID tenantId) {
        log.debug("[StubStorageUsageAdapter] tenantId={} returning 0 (no authoritative storage-accounting source in platform)",
            tenantId);
        return 0L;
    }
}
