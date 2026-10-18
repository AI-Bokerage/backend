package com.company.bsmsvc.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.dto.request.CreateInvoiceRequest;
import com.company.bsmsvc.api.dto.response.InvoiceResponse;
import com.company.bsmsvc.api.mapper.InvoiceApiMapper;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.storage.InvoiceDocumentAccessService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InvoiceController.class)
@Import(GlobalExceptionHandler.class)
class InvoiceControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InvoiceService invoiceService;
    @MockitoBean private InvoiceApiMapper invoiceApiMapper;
    @MockitoBean private InvoiceDocumentAccessService invoiceDocumentAccessService;

    @Test
    void createInvoiceShouldReturnCreated() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        String invoiceNumber = "INV-TEST";

        // Request now only carries tenantId + subscriptionId;
        // period, currency, and line items are derived from the subscription inside the service.
        CreateInvoiceRequest request = new CreateInvoiceRequest(tenantId, subscriptionId);

        PlatformInvoice draft = PlatformInvoice.builder()
            .tenantId(tenantId).subscriptionId(subscriptionId).build();

        PlatformInvoice saved = draft.toBuilder()
            .id(invoiceId).invoiceNumber(invoiceNumber)
            .status(InvoiceStatus.OPEN).amountDue(9900L).amountPaid(0L)
            .currency("INR")
            .periodStart(Instant.parse("2026-06-01T00:00:00Z"))
            .periodEnd(Instant.parse("2026-07-01T00:00:00Z"))
            .dueDate(LocalDate.parse("2026-06-08"))
            .lineItems(List.of()).build();

        InvoiceResponse response = new InvoiceResponse(
            invoiceId, tenantId, subscriptionId, invoiceNumber, InvoiceStatus.OPEN,
            InvoiceSource.MANUAL, 9900L, 0L, "INR",
            saved.getPeriodStart(), saved.getPeriodEnd(), saved.getDueDate(),
            null, null, null, List.of());

        when(invoiceApiMapper.toDomain(request)).thenReturn(draft);
        when(invoiceService.createInvoice(draft)).thenReturn(saved);
        when(invoiceApiMapper.toResponse(saved)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"subscriptionId\":\"" + subscriptionId + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.invoiceNumber").value(invoiceNumber));
    }

    @Test
    void createInvoice_missingTenantId_returnsBadRequest() throws Exception {
        UUID subscriptionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/bsm/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subscriptionId\":\"" + subscriptionId + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createInvoice_missingSubscriptionId_returnsBadRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/bsm/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getInvoiceByIdShouldReturnInvoice() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        PlatformInvoice invoice = PlatformInvoice.builder()
            .id(invoiceId).tenantId(tenantId).subscriptionId(subscriptionId)
            .invoiceNumber("INV-TEST").status(InvoiceStatus.OPEN)
            .amountDue(1000L).amountPaid(0L).currency("INR")
            .periodStart(Instant.parse("2026-01-01T00:00:00Z"))
            .periodEnd(Instant.parse("2026-01-31T23:59:59Z"))
            .dueDate(LocalDate.parse("2026-01-08")).build();

        InvoiceResponse response = new InvoiceResponse(
            invoiceId, tenantId, subscriptionId, "INV-TEST", InvoiceStatus.OPEN,
            InvoiceSource.MANUAL, 1000L, 0L, "INR",
            invoice.getPeriodStart(), invoice.getPeriodEnd(), invoice.getDueDate(),
            null, null, null, List.of());

        when(invoiceService.getInvoiceById(invoiceId)).thenReturn(invoice);
        when(invoiceApiMapper.toResponse(invoice)).thenReturn(response);

        mockMvc.perform(get("/api/v1/bsm/invoices/{id}", invoiceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(invoiceId.toString()));
    }
}
