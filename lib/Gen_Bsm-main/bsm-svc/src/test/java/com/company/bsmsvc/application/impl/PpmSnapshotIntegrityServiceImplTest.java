package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.port.PpmVersionService;
import com.company.bsmsvc.domain.enums.PpmSnapshotStatus;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.model.PpmSnapshotDiagnostics;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.service.PpmReferenceValidator;
import com.company.bsmsvc.domain.model.PpmPlanVersionResult;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PpmSnapshotIntegrityServiceImplTest {

    @Mock
    private SubscriptionRepositoryPort subscriptionRepository;

    @Mock
    private PpmVersionService ppmVersionService;

    @InjectMocks
    private PpmSnapshotIntegrityServiceImpl service;

    // Real validator — no reason to mock a pure-field checker
    private final PpmReferenceValidator realValidator = new PpmReferenceValidator();

    private static final UUID SUB_ID      = UUID.randomUUID();
    private static final UUID PLAN_ID     = UUID.randomUUID();
    private static final UUID PRICE_ID    = UUID.randomUUID();
    private static final UUID VERSION_V1  = UUID.randomUUID();
    private static final UUID VERSION_V2  = UUID.randomUUID();

    // inject the real validator via constructor to avoid @InjectMocks wiring issues
    private PpmSnapshotIntegrityServiceImpl serviceWithRealValidator() {
        return new PpmSnapshotIntegrityServiceImpl(
            subscriptionRepository, ppmVersionService, realValidator);
    }

    // -----------------------------------------------------------------------
    // Not-found guard
    // -----------------------------------------------------------------------

    @Test
    void diagnose_subscriptionNotFound_throwsSubscriptionNotFoundException() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWithRealValidator().diagnose(SUB_ID))
            .isInstanceOf(SubscriptionNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // NOT_PPM_BACKED
    // -----------------------------------------------------------------------

    @Test
    void diagnose_bsmNativeSubscription_returnsNotPpmBacked() {
        Subscription sub = Subscription.builder().build();
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));

        PpmSnapshotDiagnostics result = serviceWithRealValidator().diagnose(SUB_ID);

        assertThat(result.status()).isEqualTo(PpmSnapshotStatus.NOT_PPM_BACKED);
        assertThat(result.ppmBacked()).isFalse();
        assertThat(result.grandfathered()).isNull();
        assertThat(result.catalogVersionNo()).isNull();
        assertThat(result.lockedVersionId()).isNull();
        assertThat(result.latestVersionId()).isNull();
    }

    // -----------------------------------------------------------------------
    // PARTIAL_REFERENCE
    // -----------------------------------------------------------------------

    @Test
    void diagnose_partialPpmReference_returnsPartialReference() {
        Subscription sub = Subscription.builder()
            .ppmPlanId(PLAN_ID)
            // the other 3 fields intentionally missing
            .build();
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));

        PpmSnapshotDiagnostics result = serviceWithRealValidator().diagnose(SUB_ID);

        assertThat(result.status()).isEqualTo(PpmSnapshotStatus.PARTIAL_REFERENCE);
        assertThat(result.ppmBacked()).isTrue();
        assertThat(result.lockedVersionId()).isNull();
    }

    // -----------------------------------------------------------------------
    // HEALTHY — not grandfathered
    // -----------------------------------------------------------------------

    @Test
    void diagnose_ppmBacked_lockedVersionMatchesLatest_returnsHealthyNotGrandfathered() {
        Subscription sub = fullyPopulatedSub(VERSION_V1);
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));
        when(ppmVersionService.getLatestVersion(PLAN_ID))
            .thenReturn(latestVersion(VERSION_V1, 1));

        PpmSnapshotDiagnostics result = serviceWithRealValidator().diagnose(SUB_ID);

        assertThat(result.status()).isEqualTo(PpmSnapshotStatus.HEALTHY);
        assertThat(result.ppmBacked()).isTrue();
        assertThat(result.grandfathered()).isFalse();
        assertThat(result.catalogVersionNo()).isEqualTo(1);
        assertThat(result.lockedVersionId()).isEqualTo(VERSION_V1);
        assertThat(result.latestVersionId()).isEqualTo(VERSION_V1);
    }

    // -----------------------------------------------------------------------
    // HEALTHY — grandfathered
    // -----------------------------------------------------------------------

    @Test
    void diagnose_ppmBacked_lockedVersionDiffersFromLatest_returnsHealthyGrandfathered() {
        // Subscription locked onto V1; catalog has advanced to V2
        Subscription sub = fullyPopulatedSub(VERSION_V1);
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));
        when(ppmVersionService.getLatestVersion(PLAN_ID))
            .thenReturn(latestVersion(VERSION_V2, 2));

        PpmSnapshotDiagnostics result = serviceWithRealValidator().diagnose(SUB_ID);

        assertThat(result.status()).isEqualTo(PpmSnapshotStatus.HEALTHY);
        assertThat(result.grandfathered()).isTrue();
        assertThat(result.catalogVersionNo()).isEqualTo(2);
        assertThat(result.lockedVersionId()).isEqualTo(VERSION_V1);
        assertThat(result.latestVersionId()).isEqualTo(VERSION_V2);
    }

    // -----------------------------------------------------------------------
    // PPM_UNAVAILABLE
    // -----------------------------------------------------------------------

    @Test
    void diagnose_ppmVersionServiceThrows_returnsPpmUnavailable() {
        Subscription sub = fullyPopulatedSub(VERSION_V1);
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));
        when(ppmVersionService.getLatestVersion(PLAN_ID))
            .thenThrow(new PpmIntegrationException("PPM circuit breaker open"));

        PpmSnapshotDiagnostics result = serviceWithRealValidator().diagnose(SUB_ID);

        assertThat(result.status()).isEqualTo(PpmSnapshotStatus.PPM_UNAVAILABLE);
        assertThat(result.ppmBacked()).isTrue();
        assertThat(result.grandfathered()).isNull();
        assertThat(result.catalogVersionNo()).isNull();
        assertThat(result.lockedVersionId()).isEqualTo(VERSION_V1);
        assertThat(result.latestVersionId()).isNull();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Subscription fullyPopulatedSub(UUID lockedVersionId) {
        return Subscription.builder()
            .ppmPlanId(PLAN_ID)
            .ppmPriceId(PRICE_ID)
            .ppmPlanVersionId(lockedVersionId)
            .ppmResolvedPriceMinor(99900L)
            .build();
    }

    private PpmPlanVersionResult latestVersion(UUID versionId, int versionNo) {
        return new PpmPlanVersionResult(
            versionId, PLAN_ID, versionNo,
            LocalDate.of(2025, 1, 1), null, true);
    }
}
