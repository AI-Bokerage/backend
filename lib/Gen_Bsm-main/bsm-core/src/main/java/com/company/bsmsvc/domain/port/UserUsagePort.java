package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.UserUsageCounts;
import java.util.UUID;

/**
 * Outbound port: queries current user counts from ADM-SVC.
 */
public interface UserUsagePort {

    /**
     * Returns both active user counts in a single fetch.
     * Preferred over the individual methods — avoids two HTTP round-trips per operation.
     *
     * @param tenantId the tenant to query
     * @return combined active internal and client user counts
     */
    UserUsageCounts getUserUsageCounts(UUID tenantId);

    /**
     * Returns the number of active internal users for the given tenant.
     * Delegates to {@link #getUserUsageCounts} — prefer the combined method at call sites
     * that need both counts to avoid two separate HTTP calls.
     */
    default int getActiveInternalUserCount(UUID tenantId) {
        return getUserUsageCounts(tenantId).activeInternalUsers();
    }

    /**
     * Returns the number of active client users for the given tenant.
     * Delegates to {@link #getUserUsageCounts} — prefer the combined method at call sites
     * that need both counts to avoid two separate HTTP calls.
     */
    default int getActiveClientUserCount(UUID tenantId) {
        return getUserUsageCounts(tenantId).activeClientUsers();
    }
}
