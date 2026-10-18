package com.company.bsmsvc.infrastructure.external.stub;

import com.company.bsmsvc.domain.port.FeatureEntitlementPort;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub feature entitlement adapter — active only when {@code stub} Spring profile is set.
 *
 * <p>Replaced in normal operation by {@code PlanVersionFeatureEntitlementAdapter} which
 * derives active features from the tenant's current plan version stored in BSM's own DB.
 * Activate with {@code SPRING_PROFILES_ACTIVE=stub} for testing.</p>
 */
@Slf4j
@Component
@Profile("stub")
public class StubFeatureEntitlementAdapter implements FeatureEntitlementPort {

    private static final List<String> STUB_FEATURES = List.of(
        "ADVANCED_ANALYTICS",
        "CUSTOM_REPORTS",
        "SSO",
        "CUSTOM_DOMAIN"
    );

    @Override
    public List<String> getActiveFeatureCodes(UUID tenantId) {
        log.debug("[StubFeatureEntitlementAdapter.getActiveFeatureCodes] tenantId={} returning {} stub features", tenantId, STUB_FEATURES.size());
        return STUB_FEATURES;
    }
}
