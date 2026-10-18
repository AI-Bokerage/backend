package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.company.bsmsvc.application.service.SubscriptionSynchronizationService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;

import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.ProrationMode;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.ProrationPreview;
import com.company.bsmsvc.domain.model.ProrationPreviewFilter;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionLimitSnapshot;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;

@ExtendWith(MockitoExtension.class)
class CommercialEngineServiceImplTest {

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
    @Mock
    private SubscriptionSynchronizationService subscriptionSynchronizationService;
    @Mock
    private TenantBillingProfileService tenantBillingProfileService;

    @Spy
    private SubscriptionLifecycleMapper subscriptionLifecycleMapper = new SubscriptionLifecycleMapper();

    @Mock private TenantScopePort tenantScopeEnforcer;
    @Mock private com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort subscriptionEventPublisher;
    @Mock private PlanVersionMetaPort ppmVersionMetaClient;
    @Mock private com.company.bsmsvc.domain.port.PlanLimitsPort ppmPlanLimitsClient;

    @InjectMocks
    private CommercialEngineServiceImpl commercialEngineService;


    @org.junit.jupiter.api.BeforeEach
    void setupEnforcer() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
    }


    @org.junit.jupiter.api.BeforeEach
    void setupEventPublisher() {
        lenient().doNothing().when(subscriptionEventPublisher).publishCreated(any());
        lenient().doNothing().when(subscriptionEventPublisher).publishChanged(any(), any(), any());
        lenient().doNothing().when(subscriptionEventPublisher).publishCanceled(any());
        lenient().doNothing().when(subscriptionEventPublisher).publishExpired(any());
        lenient().doNothing().when(subscriptionEventPublisher).publishUpgraded(any(), any());
        lenient().doNothing().when(subscriptionEventPublisher).publishRenewed(any());
    }

    @Test
    void getLimitSnapshotsShouldDelegateToRepositoryFilteringAtDatabaseLevel() {
        UUID subscriptionId = UUID.randomUUID();
        SubscriptionLimitSnapshot snapshot = SubscriptionLimitSnapshot.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscriptionId)
            .planVersionId(UUID.randomUUID())
            .limitsSnapshot(Map.of("maxInternalUsers", 10))
            .usageSnapshot(Map.of("activeInternalUsers", 12))
            .overLimit(true)
            .createdAt(Instant.now())
            .build();
        PageResult<SubscriptionLimitSnapshot> page = new PageResult<>(List.of(snapshot), 0, 20, 1, 1, false);

        when(subscriptionLimitSnapshotRepositoryPort.findSnapshots(any(), any(Integer.class), any(Integer.class), any(), any()))
            .thenReturn(page);

        PageResult<SubscriptionLimitSnapshot> result = commercialEngineService.getLimitSnapshots(
            new com.company.bsmsvc.domain.model.LimitSnapshotFilter(subscriptionId, true),
            0,
            20,
            "createdAt",
            "desc"
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().isOverLimit()).isTrue();
    }

    @Test
    void getProrationPreviewsShouldDelegateToRepositoryFilteringAtDatabaseLevel() {
        UUID subscriptionId = UUID.randomUUID();
        ProrationPreview preview = ProrationPreview.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscriptionId)
            .fromPlanVersionId(UUID.randomUUID())
            .toPlanVersionId(UUID.randomUUID())
            .prorationMode(ProrationMode.FLAT)
            .currentPlanCreditMinor(100L)
            .targetPlanChargeMinor(200L)
            .proratedAmountMinor(100L)
            .currency("INR")
            .breakdown(Map.of())
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        PageResult<ProrationPreview> page = new PageResult<>(List.of(preview), 0, 20, 1, 1, false);

        when(prorationPreviewRepositoryPort.findPreviews(any(), any(Integer.class), any(Integer.class), any(), any()))
            .thenReturn(page);

        PageResult<ProrationPreview> result = commercialEngineService.getProrationPreviews(
            new ProrationPreviewFilter(subscriptionId, null, null),
            0,
            20,
            "createdAt",
            "desc"
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().getSubscriptionId()).isEqualTo(subscriptionId);
    }

    // ── PPM-backed upgrade dispatch ───────────────────────────────────────────

    @Nested
    class PpmUpgradeSubscription {

        @org.junit.jupiter.api.Test
        void ppmPath_higherTier_upgradesSaved() {
            UUID subId       = UUID.randomUUID();
            UUID tenantId    = UUID.randomUUID();
            UUID currentPpmV = UUID.randomUUID();
            UUID targetPpmV  = UUID.randomUUID();

            Subscription sub = ppmSubscription(subId, tenantId, currentPpmV);
            when(subscriptionRepositoryPort.findById(subId)).thenReturn(Optional.of(sub));
            when(subscriptionRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

            when(ppmVersionMetaClient.getVersionMeta(currentPpmV))
                .thenReturn(ppmMeta(currentPpmV, "starter"));
            when(ppmVersionMetaClient.getVersionMeta(targetPpmV))
                .thenReturn(ppmMeta(targetPpmV, "growth"));

            when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(subscriptionEventRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(subscriptionSynchronizationService.syncUpdate(any())).thenReturn(null);

            Subscription result = commercialEngineService.upgradeSubscription(
                subId, tenantId, targetPpmV, "scaling up", "admin");

            assertThat(result.getPpmPlanVersionId()).isEqualTo(targetPpmV);
        }

        @org.junit.jupiter.api.Test
        void ppmPath_lowerTier_throwsBusinessRuleViolation() {
            UUID subId       = UUID.randomUUID();
            UUID tenantId    = UUID.randomUUID();
            UUID currentPpmV = UUID.randomUUID();
            UUID targetPpmV  = UUID.randomUUID();

            Subscription sub = ppmSubscription(subId, tenantId, currentPpmV);
            when(subscriptionRepositoryPort.findById(subId)).thenReturn(Optional.of(sub));

            when(ppmVersionMetaClient.getVersionMeta(currentPpmV))
                .thenReturn(ppmMeta(currentPpmV, "scale"));
            when(ppmVersionMetaClient.getVersionMeta(targetPpmV))
                .thenReturn(ppmMeta(targetPpmV, "starter"));

            assertThrows(BusinessRuleViolationException.class, () ->
                commercialEngineService.upgradeSubscription(subId, tenantId, targetPpmV, "test", "admin"));

            verify(subscriptionRepositoryPort, never()).save(any());
        }

        @org.junit.jupiter.api.Test
        void ppmPath_samePpmVersion_throwsBusinessRuleViolation() {
            UUID subId      = UUID.randomUUID();
            UUID tenantId   = UUID.randomUUID();
            UUID currentPpmV = UUID.randomUUID();

            Subscription sub = ppmSubscription(subId, tenantId, currentPpmV);
            when(subscriptionRepositoryPort.findById(subId)).thenReturn(Optional.of(sub));

            assertThrows(BusinessRuleViolationException.class, () ->
                commercialEngineService.upgradeSubscription(subId, tenantId, currentPpmV, "same", "admin"));

            verify(ppmVersionMetaClient, never()).getVersionMeta(any());
        }
    }

    // ── PPM-backed scheduled plan change dispatch ─────────────────────────────

    @Nested
    class PpmApplyScheduledPlanChange {

        @org.junit.jupiter.api.Test
        void ppmPath_lowerTier_downgradesSaved() {
            UUID subId       = UUID.randomUUID();
            UUID tenantId    = UUID.randomUUID();
            UUID currentPpmV = UUID.randomUUID();
            UUID targetPpmV  = UUID.randomUUID();

            Subscription sub = ppmSubscription(subId, tenantId, currentPpmV);
            when(subscriptionRepositoryPort.findById(subId)).thenReturn(Optional.of(sub));
            when(subscriptionRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

            when(ppmVersionMetaClient.getVersionMeta(currentPpmV))
                .thenReturn(ppmMeta(currentPpmV, "growth"));
            when(ppmVersionMetaClient.getVersionMeta(targetPpmV))
                .thenReturn(ppmMeta(targetPpmV, "starter"));

            when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(subscriptionEventRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(subscriptionSynchronizationService.syncUpdate(any())).thenReturn(null);

            Subscription result = commercialEngineService.applyScheduledPlanChange(
                subId, tenantId, targetPpmV, "Scheduled downgrade");

            assertThat(result.getPpmPlanVersionId()).isEqualTo(targetPpmV);
        }

        @org.junit.jupiter.api.Test
        void ppmPath_higherTierTarget_throwsBusinessRuleViolation() {
            UUID subId       = UUID.randomUUID();
            UUID tenantId    = UUID.randomUUID();
            UUID currentPpmV = UUID.randomUUID();
            UUID targetPpmV  = UUID.randomUUID();

            Subscription sub = ppmSubscription(subId, tenantId, currentPpmV);
            when(subscriptionRepositoryPort.findById(subId)).thenReturn(Optional.of(sub));

            when(ppmVersionMetaClient.getVersionMeta(currentPpmV))
                .thenReturn(ppmMeta(currentPpmV, "starter"));
            when(ppmVersionMetaClient.getVersionMeta(targetPpmV))
                .thenReturn(ppmMeta(targetPpmV, "enterprise"));

            assertThrows(BusinessRuleViolationException.class, () ->
                commercialEngineService.applyScheduledPlanChange(subId, tenantId, targetPpmV, "bad schedule"));

            verify(subscriptionRepositoryPort, never()).save(any());
        }
    }

    private Subscription ppmSubscription(UUID subscriptionId, UUID tenantId, UUID ppmPlanVersionId) {
        Instant now = Instant.now();
        return Subscription.builder()
            .id(subscriptionId)
            .tenantId(tenantId)
            .ppmPlanVersionId(ppmPlanVersionId)
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(now.minusSeconds(3600))
            .currentPeriodEnd(now.plusSeconds(3600))
            .build();
    }

    private PpmVersionMetaResult ppmMeta(UUID versionId, String tier) {
        return new PpmVersionMetaResult(
            versionId, UUID.randomUUID(), tier.toUpperCase(), 1,
            true, true, LocalDate.of(2026, 1, 1), null, tier);
    }
}
