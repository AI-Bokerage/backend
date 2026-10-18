package com.company.bsmsvc.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.dto.response.PaymentMethodResponse;
import com.company.bsmsvc.api.dto.response.PaymentMethodSummaryResponse;
import com.company.bsmsvc.api.mapper.PaymentMethodApiMapper;
import com.company.bsmsvc.application.service.PaymentMethodService;
import com.company.bsmsvc.domain.enums.PaymentMethodStatus;
import com.company.bsmsvc.domain.enums.PaymentMethodType;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.exception.PaymentMethodNotFoundException;
import com.company.bsmsvc.domain.model.PaymentMethod;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PaymentMethodController.class)
@Import(GlobalExceptionHandler.class)
class PaymentMethodControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentMethodService paymentMethodService;
    @MockitoBean private PaymentMethodApiMapper mapper;

    @Test
    void addPaymentMethod_returns201() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID pmId = UUID.randomUUID();
        TenantBillingProfile profile = TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).paymentProvider(PaymentProvider.STRIPE)
            .externalCustomerId("cus_123").createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
        PaymentMethod pm = paymentMethod(pmId, tenantId);
        PaymentMethodResponse response = pmResponse(pmId, tenantId);

        when(paymentMethodService.createCustomerIfRequired(eq(tenantId), any(), any())).thenReturn(profile);
        when(paymentMethodService.addPaymentMethod(any(), any(), any(), any(), any(), any(), any(), any(boolean.class))).thenReturn(pm);
        when(mapper.toResponse(pm)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"paymentMethodToken\":\"pm_test\",\"type\":\"CARD\",\"makeDefault\":false}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(pmId.toString()));
    }

    @Test
    void addPaymentMethod_returns400_onMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/bsm/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listPaymentMethods_returns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID pmId = UUID.randomUUID();
        PaymentMethod pm = paymentMethod(pmId, tenantId);
        PaymentMethodSummaryResponse summary = new PaymentMethodSummaryResponse(pmId, PaymentMethodType.CARD, "visa", "4242", 12, 2028, false, PaymentMethodStatus.ACTIVE);

        when(paymentMethodService.listPaymentMethods(tenantId)).thenReturn(List.of(pm));
        when(mapper.toSummary(pm)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/bsm/payment-methods").param("tenantId", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(pmId.toString()));
    }

    @Test
    void removePaymentMethod_returns204() throws Exception {
        UUID pmId = UUID.randomUUID();
        doNothing().when(paymentMethodService).removePaymentMethod(pmId);

        mockMvc.perform(delete("/api/v1/bsm/payment-methods/{id}", pmId))
            .andExpect(status().isNoContent());
    }

    @Test
    void removePaymentMethod_returns404_whenNotFound() throws Exception {
        UUID pmId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new PaymentMethodNotFoundException("Not found: " + pmId))
            .when(paymentMethodService).removePaymentMethod(pmId);

        mockMvc.perform(delete("/api/v1/bsm/payment-methods/{id}", pmId))
            .andExpect(status().isNotFound());
    }

    @Test
    void setDefault_returns200() throws Exception {
        UUID pmId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        PaymentMethod pm = paymentMethod(pmId, tenantId);
        PaymentMethodResponse response = pmResponse(pmId, tenantId);

        when(paymentMethodService.setDefaultPaymentMethod(pmId)).thenReturn(pm);
        when(mapper.toResponse(pm)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/bsm/payment-methods/{id}/default", pmId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    private PaymentMethod paymentMethod(UUID id, UUID tenantId) {
        return PaymentMethod.builder()
            .id(id).tenantId(tenantId).paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentMethodId("pm_test").type(PaymentMethodType.CARD)
            .brand("visa").lastFour("4242").expMonth(12).expYear(2028)
            .isDefault(false).status(PaymentMethodStatus.ACTIVE)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }

    private PaymentMethodResponse pmResponse(UUID id, UUID tenantId) {
        return new PaymentMethodResponse(id, tenantId, PaymentProvider.STRIPE,
            "pm_test", PaymentMethodType.CARD, "visa", "4242", 12, 2028,
            false, PaymentMethodStatus.ACTIVE, Instant.now(), Instant.now());
    }
}
