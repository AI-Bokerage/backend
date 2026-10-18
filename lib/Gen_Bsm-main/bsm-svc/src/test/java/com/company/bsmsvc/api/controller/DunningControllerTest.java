package com.company.bsmsvc.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.mapper.DunningApiMapper;
import com.company.bsmsvc.application.service.DunningService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.DunningStatus;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.api.dto.response.DunningStatusResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DunningController.class)
@Import(GlobalExceptionHandler.class)
class DunningControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DunningService dunningService;
    @MockitoBean private DunningApiMapper mapper;
    @MockitoBean private SubscriptionRepositoryPort subscriptionRepository;

    @Test
    void getDunningStatus_returns200() throws Exception {
        UUID subId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
            .id(subId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.PAST_DUE).billingCycle(BillingCycle.MONTHLY)
            .dunningStatus(DunningStatus.DUNNING_DAY_1).dunningStartedAt(Instant.now())
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .domainEvents(new ArrayList<>()).build();
        DunningStatusResponse resp = new DunningStatusResponse(subId, tenantId, DunningStatus.DUNNING_DAY_1,
            Instant.now(), null, 1, List.of());

        when(subscriptionRepository.findById(subId)).thenReturn(Optional.of(sub));
        when(dunningService.getAttempts(subId)).thenReturn(List.of());
        when(mapper.toDunningStatus(any(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/v1/bsm/dunning/{id}", subId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.dunningStatus").value("DUNNING_DAY_1"));
    }

    @Test
    void getDunningStatus_returns404_whenNotFound() throws Exception {
        UUID subId = UUID.randomUUID();
        when(subscriptionRepository.findById(subId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/bsm/dunning/{id}", subId))
            .andExpect(status().isNotFound());
    }

    @Test
    void manualRetry_returns200() throws Exception {
        UUID subId = UUID.randomUUID();
        doNothing().when(dunningService).manualRetry(subId);

        mockMvc.perform(post("/api/v1/bsm/dunning/{id}/retry", subId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAttempts_returns200() throws Exception {
        UUID subId = UUID.randomUUID();
        when(dunningService.getAttempts(subId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bsm/dunning/attempts").param("subscriptionId", subId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }
}
