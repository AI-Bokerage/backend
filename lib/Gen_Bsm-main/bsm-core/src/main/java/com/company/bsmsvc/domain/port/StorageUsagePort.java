package com.company.bsmsvc.domain.port;

import java.util.UUID;

/**
 * Outbound port: queries current storage consumption from USG-SVC.
 * Stub adapter active in Phase 3; replaced by real integration in Phase 4.
 */
public interface StorageUsagePort {

    /**
     * Returns the total consumed storage in bytes for the given tenant.
     *
     * @param tenantId the tenant to query
     * @return used storage in bytes
     */
    long getUsedStorageBytes(UUID tenantId);
}
