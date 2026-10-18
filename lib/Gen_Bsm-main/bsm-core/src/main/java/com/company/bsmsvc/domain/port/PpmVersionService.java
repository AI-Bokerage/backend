package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PpmPlanVersionResult;
import java.util.UUID;

/**
 * Service abstraction over PPM-SVC's plan version lookup.
 *
 * <p>Implementations apply a circuit breaker (fail-closed) so that a
 * degraded PPM-SVC aborts checkout rather than storing a null version.
 */
public interface PpmVersionService {

    /**
     * Returns the latest active version for a PPM plan.
     *
     * @param ppmPlanId PPM plan UUID
     * @return the latest version; never {@code null}
     * @throws com.company.bsmsvc.domain.exception.PpmIntegrationException if PPM is
     *         unavailable or the circuit breaker is open
     */
    PpmPlanVersionResult getLatestVersion(UUID ppmPlanId);
}
