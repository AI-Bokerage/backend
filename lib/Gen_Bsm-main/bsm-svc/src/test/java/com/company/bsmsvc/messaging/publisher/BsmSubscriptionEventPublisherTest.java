package com.company.bsmsvc.messaging.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.infrastructure.client.ppm.PpmVersionMetaClient;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import com.company.bsmsvc.infrastructure.outbox.BsmOutboxService;
import com.company.bsmsvc.messaging.BsmSubscriptionEventPublisher;
import com.company.bsmsvc.messaging.SubscriptionEventPayload;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionEventPublisherPort")
class BsmSubscriptionEventPublisherTest {

    static final UUID TENANT_ID       = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID PPM_PLAN_ID     = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID PPM_VERSION_ID  = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Mock BsmOutboxService      outboxService;
    @Mock PpmVersionMetaClient  ppmVersionMetaClient;

    @InjectMocks BsmSubscriptionEventPublisher publisher;

    private Subscription ppmBackedSubscription() {
        return Subscription.builder()
            .id(SUBSCRIPTION_ID)
            .tenantId(TENANT_ID)
            .planVersionId(null)
            .ppmPlanId(PPM_PLAN_ID)
            .ppmPlanVersionId(PPM_VERSION_ID)
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .build();
    }

    private PpmVersionMetaResult ppmMeta(String planCode) {
        return new PpmVersionMetaResult(
            PPM_VERSION_ID, PPM_PLAN_ID, planCode, 1,
            true, true, LocalDate.of(2026, 1, 1), null, null);
    }

    private SubscriptionEventPayload capturePayload() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).save(any(), any(), any(), any(), captor.capture());
        return (SubscriptionEventPayload) captor.getValue();
    }

    // ── PPM-backed subscriptions ──────────────────────────────────────────────

    @Nested
    @DisplayName("PPM-backed subscription")
    class PpmBacked {

        @Test
        @DisplayName("publishCreated — resolves planCode from PPM meta")
        void publishCreated_callsPpmMeta() {
            Subscription sub = ppmBackedSubscription();
            when(ppmVersionMetaClient.getVersionMeta(PPM_VERSION_ID)).thenReturn(ppmMeta("PRO"));

            publisher.publishCreated(sub);

            verify(ppmVersionMetaClient).getVersionMeta(PPM_VERSION_ID);

            SubscriptionEventPayload payload = capturePayload();
            assertThat(payload.newPlanCode()).isEqualTo("PRO");
            assertThat(payload.ppmPlanId()).isEqualTo(PPM_PLAN_ID);
        }

        @Test
        @DisplayName("publishRenewed — ppmPlanId propagated in payload")
        void publishRenewed_ppmPlanIdInPayload() {
            Subscription sub = ppmBackedSubscription();
            when(ppmVersionMetaClient.getVersionMeta(PPM_VERSION_ID)).thenReturn(ppmMeta("SCALE"));

            publisher.publishRenewed(sub);

            SubscriptionEventPayload payload = capturePayload();
            assertThat(payload.ppmPlanId()).isEqualTo(PPM_PLAN_ID);
            assertThat(payload.newPlanCode()).isEqualTo("SCALE");
        }

        @Test
        @DisplayName("publishCreated — PPM meta failure yields null planCode (graceful degradation)")
        void publishCreated_ppmMetaFails_nullPlanCode() {
            Subscription sub = ppmBackedSubscription();
            when(ppmVersionMetaClient.getVersionMeta(PPM_VERSION_ID))
                .thenThrow(new RuntimeException("PPM unavailable"));

            publisher.publishCreated(sub);

            SubscriptionEventPayload payload = capturePayload();
            assertThat(payload.newPlanCode()).isNull();
            assertThat(payload.ppmPlanId()).isEqualTo(PPM_PLAN_ID);
        }

        @Test
        @DisplayName("publishUpgraded — new plan code from PPM, old plan code null for non-BSM from version")
        void publishUpgraded_newCodeFromPpm_oldCodeNull() {
            UUID fromPlanVersionId = UUID.randomUUID();
            Subscription sub = ppmBackedSubscription();
            when(ppmVersionMetaClient.getVersionMeta(PPM_VERSION_ID)).thenReturn(ppmMeta("ENTERPRISE"));

            publisher.publishUpgraded(sub, fromPlanVersionId);

            SubscriptionEventPayload payload = capturePayload();
            assertThat(payload.newPlanCode()).isEqualTo("ENTERPRISE");
            assertThat(payload.oldPlanCode()).isNull();
            assertThat(payload.ppmPlanId()).isEqualTo(PPM_PLAN_ID);
        }
    }

    // ── Payload structure ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payload structure")
    class PayloadStructure {

        @Test
        @DisplayName("version field is always CURRENT_VERSION")
        void payload_versionIsCurrentVersion() {
            Subscription sub = ppmBackedSubscription();
            when(ppmVersionMetaClient.getVersionMeta(any())).thenReturn(ppmMeta("PRO"));

            publisher.publishCreated(sub);

            SubscriptionEventPayload payload = capturePayload();
            assertThat(payload.version()).isEqualTo(SubscriptionEventPayload.CURRENT_VERSION);
        }

        @Test
        @DisplayName("billingCycle is written as enum name string")
        void payload_billingCycleAsString() {
            Subscription sub = ppmBackedSubscription();
            when(ppmVersionMetaClient.getVersionMeta(any())).thenReturn(ppmMeta("PRO"));

            publisher.publishCreated(sub);

            SubscriptionEventPayload payload = capturePayload();
            assertThat(payload.billingCycle()).isEqualTo("MONTHLY");
        }
    }
}
