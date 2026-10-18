package com.company.bsmsvc.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.infrastructure.client.ppm.PpmVersionMetaClient;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class BsmInternalSubscriptionControllerTest {

    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private PpmVersionMetaClient ppmVersionMetaClient;
    @InjectMocks private BsmInternalSubscriptionController controller;

    private static final UUID TENANT     = UUID.randomUUID();
    private static final UUID SUB_ID     = UUID.randomUUID();
    private static final UUID PPM_PV_ID  = UUID.randomUUID();

    @Nested
    class ResolvePlanCode {

        @Test
        void ppmBacked_resolvesPlanCodeFromPpmMeta() {
            Subscription sub = ppmSubscription();
            PpmVersionMetaResult meta = ppmMeta("growth-monthly");
            when(subscriptionRepository.findCurrentByTenantId(TENANT)).thenReturn(Optional.of(sub));
            when(ppmVersionMetaClient.getVersionMeta(PPM_PV_ID)).thenReturn(meta);

            ResponseEntity<?> response = controller.getSummary(TENANT);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            var body = (com.company.bsmsvc.api.dto.response.ApiResponse<?>) response.getBody();
            @SuppressWarnings("unchecked")
            var data = (java.util.Map<String, Object>) body.data();
            assertThat(data.get("planCode")).isEqualTo("growth-monthly");
        }

        @Test
        void ppmBacked_metaClientThrows_planCodeIsNull() {
            Subscription sub = ppmSubscription();
            when(subscriptionRepository.findCurrentByTenantId(TENANT)).thenReturn(Optional.of(sub));
            when(ppmVersionMetaClient.getVersionMeta(PPM_PV_ID))
                .thenThrow(new com.company.bsmsvc.domain.exception.PpmIntegrationException("PPM unavailable"));

            ResponseEntity<?> response = controller.getSummary(TENANT);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            var body = (com.company.bsmsvc.api.dto.response.ApiResponse<?>) response.getBody();
            @SuppressWarnings("unchecked")
            var data = (java.util.Map<String, Object>) body.data();
            assertThat(data.get("planCode")).isNull();
        }
    }

    private Subscription ppmSubscription() {
        return Subscription.builder()
            .id(SUB_ID).tenantId(TENANT)
            .ppmPlanVersionId(PPM_PV_ID)
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private PpmVersionMetaResult ppmMeta(String planCode) {
        return new PpmVersionMetaResult(PPM_PV_ID, UUID.randomUUID(), planCode, 1,
            true, true, LocalDate.now(), null, "growth");
    }
}
