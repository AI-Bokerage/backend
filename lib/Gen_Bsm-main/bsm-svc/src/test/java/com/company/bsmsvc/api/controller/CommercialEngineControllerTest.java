package com.company.bsmsvc.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.dto.response.DowngradePreflightResponse;
import com.company.bsmsvc.api.dto.response.DowngradeWarningDto;
import com.company.bsmsvc.api.dto.response.ProrationPreviewResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionResponse;
import com.company.bsmsvc.api.mapper.CommercialEngineApiMapper;
import com.company.bsmsvc.api.mapper.SubscriptionApiMapper;
import com.company.bsmsvc.application.service.CommercialEngineService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.ProrationMode;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.DowngradeImpact;
import com.company.bsmsvc.domain.model.DowngradeImpactDetails;
import com.company.bsmsvc.domain.model.ProrationPreview;
import com.company.bsmsvc.domain.model.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CommercialEngineController.class)
@Import(GlobalExceptionHandler.class)
class CommercialEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommercialEngineService commercialEngineService;
    @MockitoBean
    private SubscriptionApiMapper subscriptionApiMapper;
    @MockitoBean
    private CommercialEngineApiMapper commercialEngineApiMapper;

    @Test
    void upgradeSubscriptionShouldReturnSuccessPayload() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID targetPlanVersionId = UUID.randomUUID();
        Subscription subscription = Subscription.builder()
            .id(subscriptionId)
            .tenantId(tenantId)
            .planVersionId(targetPlanVersionId)
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .build();
        SubscriptionResponse response = new SubscriptionResponse(
            subscriptionId, tenantId, targetPlanVersionId, SubscriptionStatus.ACTIVE, BillingCycle.MONTHLY,
            null, null, null, null, false, null, null, null,
            null, null, null
        );

        when(commercialEngineService.upgradeSubscription(subscriptionId, tenantId, targetPlanVersionId, "Upgrade", "tester"))
            .thenReturn(subscription);
        when(subscriptionApiMapper.toResponse(subscription)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/subscriptions/{id}/upgrade", subscriptionId)
                .contentType("application/json")
                .content("""
                    {
                      "tenantId": "%s",
                      "targetPlanVersionId": "%s",
                      "reason": "Upgrade",
                      "performedBy": "tester"
                    }
                    """.formatted(tenantId, targetPlanVersionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.planVersionId").value(targetPlanVersionId.toString()));
    }

    @Test
    void generateProrationPreviewShouldReturnSuccessPayload() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID targetPlanVersionId = UUID.randomUUID();
        ProrationPreview preview = ProrationPreview.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscriptionId)
            .fromPlanVersionId(UUID.randomUUID())
            .toPlanVersionId(targetPlanVersionId)
            .prorationMode(ProrationMode.FLAT)
            .currentPlanCreditMinor(100L)
            .targetPlanChargeMinor(200L)
            .proratedAmountMinor(100L)
            .currency("INR")
            .breakdown(Map.of())
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        ProrationPreviewResponse response = new ProrationPreviewResponse(
            preview.getId(), subscriptionId, preview.getFromPlanVersionId(), targetPlanVersionId,
            ProrationMode.FLAT, 100L, 200L, 100L, "INR", Map.of(), preview.getExpiresAt(), preview.getCreatedAt()
        );

        when(commercialEngineService.generateProrationPreview(subscriptionId, tenantId, targetPlanVersionId, ProrationMode.FLAT))
            .thenReturn(preview);
        when(commercialEngineApiMapper.toResponse(preview)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/subscriptions/{id}/proration-preview", subscriptionId)
                .contentType("application/json")
                .content("""
                    {
                      "tenantId": "%s",
                      "targetPlanVersionId": "%s",
                      "prorationMode": "FLAT"
                    }
                    """.formatted(tenantId, targetPlanVersionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.delta").value(100));
    }

    @Test
    void executeDowngradePreflightShouldReturnSuccessPayload() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID targetPlanVersionId = UUID.randomUUID();
        DowngradeImpact impact = new DowngradeImpact(
            new DowngradeImpactDetails(2, 1, 512L, List.of("SSO")),
            List.of(new com.company.bsmsvc.domain.model.DowngradeWarning("FEATURES_WILL_BE_LOST", "SSO will be lost"))
        );
        DowngradePreflightResponse response = new DowngradePreflightResponse(
            2, 1, 512L, List.of("SSO"), List.of(new DowngradeWarningDto("FEATURES_WILL_BE_LOST", "SSO will be lost"))
        );

        when(commercialEngineService.executeDowngradePreflight(subscriptionId, tenantId, targetPlanVersionId)).thenReturn(impact);
        when(commercialEngineApiMapper.toResponse(impact)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/subscriptions/{id}/downgrade/preflight", subscriptionId)
                .contentType("application/json")
                .content("""
                    {
                      "tenantId": "%s",
                      "targetPlanVersionId": "%s"
                    }
                    """.formatted(tenantId, targetPlanVersionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.usersOverLimit").value(2))
            .andExpect(jsonPath("$.data.featuresLost[0]").value("SSO"));
    }
}
