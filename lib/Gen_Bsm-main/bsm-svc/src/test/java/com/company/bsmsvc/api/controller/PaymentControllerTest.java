package com.company.bsmsvc.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.mapper.PaymentApiMapper;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.domain.exception.PaymentNotFoundException;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.model.payment.PaymentIntentResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentService paymentService;
    @MockitoBean private PaymentApiMapper mapper;

    @Test
    void createCheckoutSession_returns201() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(paymentService.createCheckoutSession(any(), any(), any(), any()))
            .thenReturn(new CheckoutSessionResult("cs_test_123", "https://checkout.stripe.com"));

        mockMvc.perform(post("/api/v1/bsm/payments/checkout-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"invoiceId\":\"" + invoiceId
                    + "\",\"successUrl\":\"https://ok\",\"cancelUrl\":\"https://cancel\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sessionId").value("cs_test_123"));
    }

    @Test
    void createPaymentIntent_returns201() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(paymentService.createPaymentIntent(any(), any()))
            .thenReturn(new PaymentIntentResult("pi_test_123", "pi_test_secret", "requires_payment_method"));

        mockMvc.perform(post("/api/v1/bsm/payments/payment-intents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"invoiceId\":\"" + invoiceId + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.paymentIntentId").value("pi_test_123"));
    }

    @Test
    void getPayment_returns404_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.getPayment(id)).thenThrow(new PaymentNotFoundException("Not found: " + id));

        mockMvc.perform(get("/api/v1/bsm/payments/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }
}
