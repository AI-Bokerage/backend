package com.company.bsmsvc.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.dto.request.CancelSubscriptionRequest;
import com.company.bsmsvc.api.dto.response.SubscriptionResponse;
import com.company.bsmsvc.api.mapper.SubscriptionApiMapper;
import com.company.bsmsvc.api.mapper.SubscriptionQueryApiMapper;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SubscriptionController.class)
@Import(GlobalExceptionHandler.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private SubscriptionApiMapper subscriptionApiMapper;

    @MockitoBean
    private SubscriptionQueryApiMapper subscriptionQueryApiMapper;

    @Test
    void getCurrentSubscriptionShouldReturnSuccessPayload() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now())
            .currentPeriodEnd(Instant.now().plusSeconds(3600))
            .build();
        SubscriptionResponse response = new SubscriptionResponse(
            subscription.getId(),
            subscription.getTenantId(),
            subscription.getPlanVersionId(),
            subscription.getStatus(),
            subscription.getBillingCycle(),
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd(),
            subscription.getTrialEndsAt(),
            subscription.getCancelledAt(),
            subscription.isCancelAtPeriodEnd(),
            subscription.getVersion(),
            subscription.getCreatedAt(),
            subscription.getUpdatedAt(),
            null, null, null
        );

        when(subscriptionService.getCurrentSubscription(tenantId)).thenReturn(subscription);
        when(subscriptionApiMapper.toResponse(subscription)).thenReturn(response);

        mockMvc.perform(get("/api/v1/bsm/subscriptions/current").param("tenantId", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.tenantId").value(tenantId.toString()));
    }

    @Test
    void cancelSubscriptionShouldReturnSuccessPayload() throws Exception {
        UUID tenantId = UUID.randomUUID();
        CancelSubscriptionRequest request = new CancelSubscriptionRequest(tenantId, "tester", "requested", true);
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.CANCELLED)
            .billingCycle(BillingCycle.MONTHLY)
            .cancelledAt(Instant.now())
            .build();
        SubscriptionResponse response = new SubscriptionResponse(
            subscription.getId(),
            subscription.getTenantId(),
            subscription.getPlanVersionId(),
            subscription.getStatus(),
            subscription.getBillingCycle(),
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd(),
            subscription.getTrialEndsAt(),
            subscription.getCancelledAt(),
            subscription.isCancelAtPeriodEnd(),
            subscription.getVersion(),
            subscription.getCreatedAt(),
            subscription.getUpdatedAt(),
            null, null, null
        );

        when(subscriptionService.cancelSubscription(tenantId, "requested", "tester", true)).thenReturn(subscription);
        when(subscriptionApiMapper.toResponse(subscription)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/subscriptions/cancel")
                .contentType("application/json")
            .content("{\"tenantId\":\"" + tenantId + "\",\"performedBy\":\"tester\",\"reason\":\"requested\",\"cancelImmediately\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

}
