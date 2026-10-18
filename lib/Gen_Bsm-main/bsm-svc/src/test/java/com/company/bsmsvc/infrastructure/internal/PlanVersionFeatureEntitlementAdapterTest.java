package com.company.bsmsvc.infrastructure.internal;

import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.infrastructure.client.ppm.PpmPlanLimitsClient;
import com.company.bsmsvc.domain.model.PpmPlanLimitsResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanVersionFeatureEntitlementAdapterTest {

    @Mock  private SubscriptionRepositoryPort subscriptionRepository;
    @Mock  private PpmPlanLimitsClient ppmPlanLimitsClient;
    @InjectMocks private PlanVersionFeatureEntitlementAdapter adapter;

    private static final UUID TENANT     = UUID.randomUUID();
    private static final UUID SUB_ID     = UUID.randomUUID();
    private static final UUID PPM_PV_ID  = UUID.randomUUID();

    private Subscription ppmSubscription() {
        return Subscription.builder()
            .id(SUB_ID).tenantId(TENANT).ppmPlanVersionId(PPM_PV_ID)
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(3600))
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private PpmPlanLimitsResult ppmLimits(Boolean sso, Boolean customDomain, Boolean priority) {
        return new PpmPlanLimitsResult(PPM_PV_ID, 10, 5, 3, 1_000_000_000L, customDomain, sso, priority);
    }

    @Test
    void noActiveSubscription_returnsEmptyList() {
        when(subscriptionRepository.findCurrentByTenantId(TENANT)).thenReturn(Optional.empty());

        assertThat(adapter.getActiveFeatureCodes(TENANT)).isEmpty();
    }

    @Nested
    class PpmBacked {

        @Test
        void allFeaturesEnabled_returnsAllThreeCodes() {
            when(subscriptionRepository.findCurrentByTenantId(TENANT)).thenReturn(Optional.of(ppmSubscription()));
            when(ppmPlanLimitsClient.getLimits(PPM_PV_ID)).thenReturn(ppmLimits(true, true, true));

            assertThat(adapter.getActiveFeatureCodes(TENANT))
                .containsExactlyInAnyOrder("SSO", "CUSTOM_DOMAIN", "PRIORITY_SUPPORT");
        }

        @Test
        void noFeaturesEnabled_returnsEmpty() {
            when(subscriptionRepository.findCurrentByTenantId(TENANT)).thenReturn(Optional.of(ppmSubscription()));
            when(ppmPlanLimitsClient.getLimits(PPM_PV_ID)).thenReturn(ppmLimits(false, false, false));

            assertThat(adapter.getActiveFeatureCodes(TENANT)).isEmpty();
        }

        @Test
        void limitsClientThrows_returnsEmptySafely() {
            when(subscriptionRepository.findCurrentByTenantId(TENANT)).thenReturn(Optional.of(ppmSubscription()));
            when(ppmPlanLimitsClient.getLimits(PPM_PV_ID))
                .thenThrow(new com.company.bsmsvc.domain.exception.PpmIntegrationException("PPM unavailable"));

            assertThat(adapter.getActiveFeatureCodes(TENANT)).isEmpty();
        }
    }
}
