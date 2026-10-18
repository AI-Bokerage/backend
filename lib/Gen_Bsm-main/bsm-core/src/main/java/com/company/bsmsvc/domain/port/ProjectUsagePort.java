package com.company.bsmsvc.domain.port;

import java.util.UUID;

/**
 * Outbound port: queries active project counts from ADM-SVC.
 * Stub adapter active in Phase 3; replaced by real integration in Phase 4.
 */
public interface ProjectUsagePort {

    /**
     * Returns the number of active projects for the given tenant.
     *
     * @param tenantId the tenant to query
     * @return count of active projects
     */
    int getActiveProjectCount(UUID tenantId);
}
