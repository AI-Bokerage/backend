package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.DowngradeImpact;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.FeatureEntitlementPort;
import com.company.bsmsvc.domain.port.ProjectUsagePort;
import com.company.bsmsvc.domain.port.ProrationPreviewRepositoryPort;
import com.company.bsmsvc.domain.port.StorageUsagePort;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionLimitSnapshotRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.UserUsagePort;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.port.PlanLimitsPort;
import com.company.bsmsvc.domain.model.PpmPlanLimitsResult;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;

@ExtendWith(MockitoExtension.class)
class DowngradePreflightTest {

    @Mock
    private SubscriptionRepositoryPort subscriptionRepositoryPort;
    @Mock
    private SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    @Mock
    private SubscriptionEventRepositoryPort subscriptionEventRepositoryPort;
    @Mock
    private ProrationPreviewRepositoryPort prorationPreviewRepositoryPort;
    @Mock
    private SubscriptionLimitSnapshotRepositoryPort subscriptionLimitSnapshotRepositoryPort;
    @Mock
    private UserUsagePort userUsagePort;
    @Mock
    private ProjectUsagePort projectUsagePort;
    @Mock
    private StorageUsagePort storageUsagePort;
    @Mock
    private FeatureEntitlementPort featureEntitlementPort;
    @Mock
    private TenantOwnershipValidator tenantOwnershipValidator;

    @Spy
    private SubscriptionLifecycleMapper subscriptionLifecycleMapper = new SubscriptionLifecycleMapper();

    @Mock private TenantScopePort tenantScopeEnforcer;
    @Mock private PlanVersionMetaPort  ppmVersionMetaClient;
    @Mock private PlanLimitsPort   ppmPlanLimitsClient;

    @InjectMocks
    private CommercialEngineServiceImpl commercialEngineService;


    @org.junit.jupiter.api.BeforeEach
    void setupEnforcer() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── PPM-backed downgrade preflight (D2.3) ────────────────────────────────

    @Nested
    class PpmBackedDowngradePreflight {

        @org.junit.jupiter.api.Test
        void ppmPath_overLimit_returnsCorrectOverages() {
            UUID subscriptionId    = UUID.randomUUID();
            UUID tenantId          = UUID.randomUUID();
            UUID currentVersionId  = UUID.randomUUID();
            UUID targetVersionId   = UUID.randomUUID();

            Instant now = Instant.now();
            Subscription sub = Subscription.builder()
                .id(subscriptionId).tenantId(tenantId)
                .planVersionId(currentVersionId)
                .ppmPlanId(UUID.randomUUID())
                .ppmPlanVersionId(UUID.randomUUID())
                .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
                .currentPeriodStart(now.minusSeconds(3600)).currentPeriodEnd(now.plusSeconds(3600))
                .build();

            PpmVersionMetaResult targetMeta = new PpmVersionMetaResult(
                targetVersionId, UUID.randomUUID(), "BASIC", 1,
                true, true, LocalDate.of(2026, 1, 1), null, null);

            PpmPlanLimitsResult limits = new PpmPlanLimitsResult(
                targetVersionId, 10, 5, 3, 1_000L, false, false, false);

            when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(sub));
            when(ppmVersionMetaClient.getVersionMeta(targetVersionId)).thenReturn(targetMeta);
            when(ppmPlanLimitsClient.getLimits(targetVersionId)).thenReturn(limits);
            when(userUsagePort.getUserUsageCounts(tenantId))
                .thenReturn(new com.company.bsmsvc.domain.model.UserUsageCounts(15, 8));
            when(projectUsagePort.getActiveProjectCount(tenantId)).thenReturn(7);
            when(storageUsagePort.getUsedStorageBytes(tenantId)).thenReturn(2_500L);
            when(featureEntitlementPort.getActiveFeatureCodes(tenantId))
                .thenReturn(List.of("CUSTOM_DOMAIN", "SSO"));
            when(subscriptionLimitSnapshotRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(subscriptionEventRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DowngradeImpact result = commercialEngineService.executeDowngradePreflight(
                subscriptionId, tenantId, targetVersionId);

            // 15 internal over 10 limit = 5; 8 client over 5 limit = 3; total = 8
            assertThat(result.details().usersOverLimit()).isEqualTo(8);
            // 7 projects over 3 limit = 4
            assertThat(result.details().projectsOverLimit()).isEqualTo(4);
            // 2500 - 1000 = 1500 bytes over
            assertThat(result.details().storageOverLimitBytes()).isEqualTo(1_500L);
            assertThat(result.details().featuresLost()).containsExactlyInAnyOrder("CUSTOM_DOMAIN", "SSO");
            assertThat(result.isOverLimit()).isTrue();
        }

        @org.junit.jupiter.api.Test
        void ppmPath_unlimitedNullFields_noOverages() {
            UUID subscriptionId   = UUID.randomUUID();
            UUID tenantId         = UUID.randomUUID();
            UUID currentVersionId = UUID.randomUUID();
            UUID targetVersionId  = UUID.randomUUID();

            Instant now = Instant.now();
            Subscription sub = Subscription.builder()
                .id(subscriptionId).tenantId(tenantId)
                .planVersionId(currentVersionId)
                .ppmPlanId(UUID.randomUUID())
                .ppmPlanVersionId(UUID.randomUUID())
                .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
                .currentPeriodStart(now.minusSeconds(3600)).currentPeriodEnd(now.plusSeconds(3600))
                .build();

            PpmVersionMetaResult targetMeta = new PpmVersionMetaResult(
                targetVersionId, UUID.randomUUID(), "ENTERPRISE", 1,
                true, true, LocalDate.of(2026, 1, 1), null, null);

            // Null numeric caps = unlimited; boolean flags true = feature enabled
            PpmPlanLimitsResult limits = new PpmPlanLimitsResult(
                targetVersionId, null, null, null, null, true, true, true);

            when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(sub));
            when(ppmVersionMetaClient.getVersionMeta(targetVersionId)).thenReturn(targetMeta);
            when(ppmPlanLimitsClient.getLimits(targetVersionId)).thenReturn(limits);
            when(userUsagePort.getUserUsageCounts(tenantId))
                .thenReturn(new com.company.bsmsvc.domain.model.UserUsageCounts(500, 200));
            when(projectUsagePort.getActiveProjectCount(tenantId)).thenReturn(999);
            when(storageUsagePort.getUsedStorageBytes(tenantId)).thenReturn(Long.MAX_VALUE / 2);
            when(featureEntitlementPort.getActiveFeatureCodes(tenantId)).thenReturn(List.of("SSO", "CUSTOM_DOMAIN"));
            when(subscriptionLimitSnapshotRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(subscriptionEventRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DowngradeImpact result = commercialEngineService.executeDowngradePreflight(
                subscriptionId, tenantId, targetVersionId);

            assertThat(result.details().usersOverLimit()).isEqualTo(0);
            assertThat(result.details().projectsOverLimit()).isEqualTo(0);
            assertThat(result.details().storageOverLimitBytes()).isEqualTo(0L);
            assertThat(result.details().featuresLost()).isEmpty();
            assertThat(result.isOverLimit()).isFalse();
            assertThat(result.warnings()).isEmpty();
        }

        @org.junit.jupiter.api.Test
        void ppmPath_inactiveTargetVersion_throwsBusinessRuleViolation() {
            UUID subscriptionId  = UUID.randomUUID();
            UUID tenantId        = UUID.randomUUID();
            UUID targetVersionId = UUID.randomUUID();

            Instant now = Instant.now();
            Subscription sub = Subscription.builder()
                .id(subscriptionId).tenantId(tenantId)
                .planVersionId(UUID.randomUUID())
                .ppmPlanId(UUID.randomUUID())
                .ppmPlanVersionId(UUID.randomUUID())
                .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
                .currentPeriodStart(now.minusSeconds(3600)).currentPeriodEnd(now.plusSeconds(3600))
                .build();

            PpmVersionMetaResult inactiveMeta = new PpmVersionMetaResult(
                targetVersionId, UUID.randomUUID(), "OLD_PLAN", 1,
                false, true, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null);

            when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(sub));
            when(ppmVersionMetaClient.getVersionMeta(targetVersionId)).thenReturn(inactiveMeta);

            assertThrows(BusinessRuleViolationException.class, () ->
                commercialEngineService.executeDowngradePreflight(subscriptionId, tenantId, targetVersionId));

            verify(ppmPlanLimitsClient, never()).getLimits(any());
        }
    }
}
