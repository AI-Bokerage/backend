package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.PpmSnapshotDiagnostics;
import java.util.UUID;

/**
 * Read-only diagnostic service for PPM snapshot integrity.
 *
 * <p>No writes are performed. The service loads the subscription locally,
 * validates reference fields, and optionally probes PPM-SVC for catalog
 * version information. The result is suitable for surfacing in ops dashboards
 * and automated reconciliation schedulers.
 */
public interface PpmSnapshotIntegrityService {

    /**
     * Diagnoses the PPM snapshot integrity for the given subscription.
     *
     * @param subscriptionId BSM subscription UUID
     * @return diagnostics record; never {@code null}
     * @throws com.company.bsmsvc.domain.exception.NotFoundException if the subscription
     *         does not exist
     */
    PpmSnapshotDiagnostics diagnose(UUID subscriptionId);
}
