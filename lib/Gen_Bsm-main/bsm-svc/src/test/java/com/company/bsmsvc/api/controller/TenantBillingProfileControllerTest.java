package com.company.bsmsvc.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.dto.response.TenantBillingProfileResponse;
import com.company.bsmsvc.api.mapper.TenantBillingProfileApiMapper;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantBillingProfileController.class)
@Import(GlobalExceptionHandler.class)
class TenantBillingProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TenantBillingProfileService service;
    @MockitoBean private TenantBillingProfileApiMapper mapper;

    @Test
    void createProfile_returns201() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantBillingProfile profile = profile(tenantId, PaymentProvider.STRIPE);
        TenantBillingProfileResponse response = response(profile);

        when(service.createProfile(eq(tenantId), eq(PaymentProvider.STRIPE), any(), eq("USD"))).thenReturn(profile);
        when(mapper.toResponse(profile)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/billing-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"paymentProvider\":\"STRIPE\",\"currency\":\"USD\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.tenantId").value(tenantId.toString()))
            .andExpect(jsonPath("$.data.paymentProvider").value("STRIPE"));
    }

    @Test
    void createProfile_returns400_onMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/bsm/billing-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createProfile_returns400_onMissingCurrency() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/bsm/billing-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"paymentProvider\":\"STRIPE\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createProfile_returns400_onInvalidCurrencyFormat() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/bsm/billing-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"paymentProvider\":\"STRIPE\",\"currency\":\"us\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getProfile_returns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantBillingProfile profile = profile(tenantId, PaymentProvider.RAZORPAY);
        TenantBillingProfileResponse response = response(profile);

        when(service.getProfile(tenantId)).thenReturn(profile);
        when(mapper.toResponse(profile)).thenReturn(response);

        mockMvc.perform(get("/api/v1/bsm/billing-profile/{tenantId}", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.paymentProvider").value("RAZORPAY"));
    }

    @Test
    void getProfile_returns404_whenNotFound() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(service.getProfile(tenantId))
            .thenThrow(new TenantBillingProfileNotFoundException("Not found: " + tenantId));

        mockMvc.perform(get("/api/v1/bsm/billing-profile/{tenantId}", tenantId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateCurrency_returns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantBillingProfile updated = profile(tenantId, PaymentProvider.STRIPE);
        TenantBillingProfileResponse response = response(updated);

        when(service.updateCurrency(eq(tenantId), eq("EUR"))).thenReturn(updated);
        when(mapper.toResponse(updated)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/bsm/billing-profile/{tenantId}/currency", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"EUR\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateCurrency_returns400_onMissingCurrency() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/bsm/billing-profile/{tenantId}/currency", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateCurrency_returns400_onInvalidCurrencyFormat() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/bsm/billing-profile/{tenantId}/currency", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"eur\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateCurrency_returns404_whenProfileNotFound() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(service.updateCurrency(eq(tenantId), any()))
            .thenThrow(new com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException("Not found: " + tenantId));

        mockMvc.perform(patch("/api/v1/bsm/billing-profile/{tenantId}/currency", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"EUR\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateProvider_returns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantBillingProfile updated = profile(tenantId, PaymentProvider.RAZORPAY);
        TenantBillingProfileResponse response = response(updated);

        when(service.updateProvider(eq(tenantId), eq(PaymentProvider.RAZORPAY))).thenReturn(updated);
        when(mapper.toResponse(updated)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/bsm/billing-profile/{tenantId}/provider", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentProvider\":\"RAZORPAY\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.paymentProvider").value("RAZORPAY"));
    }

    @Test
    void updateProvider_returns400_onMissingProvider() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/bsm/billing-profile/{tenantId}/provider", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    private TenantBillingProfile profile(UUID tenantId, PaymentProvider provider) {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(provider).currency("USD")
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }

    private TenantBillingProfileResponse response(TenantBillingProfile p) {
        return new TenantBillingProfileResponse(
            p.getId(), p.getTenantId(), p.getPaymentProvider(),
            p.getExternalCustomerId(), p.getCurrency(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
