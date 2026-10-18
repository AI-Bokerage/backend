package com.company.bsmsvc.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port: queries active feature entitlements from FMM-SVC.
 * Stub adapter active in Phase 3; replaced by real integration in Phase 4.
 */
public interface FeatureEntitlementPort {

    /**
     * Returns the list of active feature codes for the given tenant.
     * Features are string identifiers (e.g. "ADVANCED_ANALYTICS", "SSO", "CUSTOM_DOMAIN").
     *
     * @param tenantId the tenant to query
     * @return list of active feature codes
     */
    List<String> getActiveFeatureCodes(UUID tenantId);
}
