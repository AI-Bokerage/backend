package com.company.bsmsvc.infrastructure.internal;

import com.company.bsmsvc.domain.port.FeatureEntitlementPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.infrastructure.client.ppm.PpmPlanLimitsClient;
import com.company.bsmsvc.domain.model.PpmPlanLimitsResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Real {@link FeatureEntitlementPort} implementation backed by BSM-SVC's own data.
 *
 * <p>Feature entitlements for downgrade preflight are derived entirely from the
 * tenant's current subscription plan version — no external service call required.
 * A tenant's active features are exactly what their current plan includes:</p>
 *
 * <ul>
 *   <li>{@code plan_versions.sso_enabled = true}            → feature code {@code "SSO"}</li>
 *   <li>{@code plan_versions.custom_domain_enabled = true}  → feature code {@code "CUSTOM_DOMAIN"}</li>
 *   <li>{@code plan_versions.priority_support = true}       → feature code {@code "PRIORITY_SUPPORT"}</li>
 * </ul>
 *
 * <p>This is the correct semantics for billing-driven downgrade validation: the
 * question is "which contractual features will the tenant lose?" — that answer is
 * determined by what their current paid plan provides, not by per-tenant FMM
 * overrides which are outside the billing domain.</p>
 *
 * <p>If the tenant has no active subscription, an empty list is returned and no
 * feature-loss warnings are generated.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanVersionFeatureEntitlementAdapter implements FeatureEntitlementPort {

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final PpmPlanLimitsClient ppmPlanLimitsClient;

    @Override
    public List<String> getActiveFeatureCodes(UUID tenantId) {
        return subscriptionRepository.findCurrentByTenantId(tenantId)
            .map(sub -> {
                if (sub.getPpmPlanVersionId() != null) {
                    return buildFeatureCodesFromPpm(sub.getPpmPlanVersionId());
                }
                log.debug("[PlanVersionFeatureEntitlement] No PPM version for tenantId={} — returning empty features", tenantId);
                return List.<String>of();
            })
            .orElseGet(() -> {
                log.debug("[PlanVersionFeatureEntitlement] No active subscription for tenantId={} — returning empty features", tenantId);
                return List.of();
            });
    }

    private List<String> buildFeatureCodesFromPpm(UUID ppmPlanVersionId) {
        try {
            PpmPlanLimitsResult limits = ppmPlanLimitsClient.getLimits(ppmPlanVersionId);
            List<String> codes = new ArrayList<>();
            if (Boolean.TRUE.equals(limits.ssoEnabled()))           codes.add("SSO");
            if (Boolean.TRUE.equals(limits.customDomainEnabled()))  codes.add("CUSTOM_DOMAIN");
            if (Boolean.TRUE.equals(limits.prioritySupport()))      codes.add("PRIORITY_SUPPORT");
            log.debug("[PlanVersionFeatureEntitlement] ppmPlanVersionId={} activeCodes={}", ppmPlanVersionId, codes);
            return codes;
        } catch (Exception e) {
            log.warn("[PlanVersionFeatureEntitlement] PPM limits lookup failed ppmPlanVersionId={} — returning empty features: {}",
                ppmPlanVersionId, e.getMessage());
            return List.of();
        }
    }

}
