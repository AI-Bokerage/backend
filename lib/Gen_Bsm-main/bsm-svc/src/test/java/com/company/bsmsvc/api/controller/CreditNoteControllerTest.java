package com.company.bsmsvc.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.dto.response.CreditNoteResponse;
import com.company.bsmsvc.api.mapper.CreditNoteApiMapper;
import com.company.bsmsvc.application.service.CreditNoteService;
import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.exception.CreditNoteNotFoundException;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.PageResult;
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

@WebMvcTest(controllers = CreditNoteController.class)
@Import(GlobalExceptionHandler.class)
class CreditNoteControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CreditNoteService creditNoteService;
    @MockitoBean private CreditNoteApiMapper creditNoteApiMapper;

    @Test
    void createCreditNote_returns201() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID cnId = UUID.randomUUID();
        CreditNote cn = creditNote(cnId, tenantId, CreditNoteStatus.OPEN);
        CreditNoteResponse response = creditNoteResponse(cnId, tenantId, CreditNoteStatus.OPEN);

        when(creditNoteService.createCreditNote(any(), any(), any(long.class), any(), any(), any())).thenReturn(cn);
        when(creditNoteApiMapper.toResponse(cn)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/credit-notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenantId + "\",\"invoiceId\":\"" + invoiceId
                    + "\",\"amountMinor\":1000,\"currency\":\"INR\",\"reason\":\"Billing error\",\"createdBy\":\""
                    + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(cnId.toString()));
    }

    @Test
    void createCreditNote_returns400_onValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/bsm/credit-notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listCreditNotes_returns200WithPagination() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID cnId = UUID.randomUUID();
        CreditNote cn = creditNote(cnId, tenantId, CreditNoteStatus.OPEN);
        CreditNoteResponse response = creditNoteResponse(cnId, tenantId, CreditNoteStatus.OPEN);
        PageResult<CreditNote> page = new PageResult<>(List.of(cn), 0, 20, 1, 1, false);

        when(creditNoteService.listCreditNotes(any(CreditNoteFilter.class), eq(0), eq(20), any(), any())).thenReturn(page);
        when(creditNoteApiMapper.toResponse(cn)).thenReturn(response);

        mockMvc.perform(get("/api/v1/bsm/credit-notes").param("tenantId", tenantId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(cnId.toString()))
            .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    void getCreditNoteById_returns200() throws Exception {
        UUID cnId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CreditNote cn = creditNote(cnId, tenantId, CreditNoteStatus.OPEN);
        CreditNoteResponse response = creditNoteResponse(cnId, tenantId, CreditNoteStatus.OPEN);

        when(creditNoteService.getCreditNoteById(cnId)).thenReturn(cn);
        when(creditNoteApiMapper.toResponse(cn)).thenReturn(response);

        mockMvc.perform(get("/api/v1/bsm/credit-notes/{id}", cnId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(cnId.toString()));
    }

    @Test
    void getCreditNoteById_returns404_whenNotFound() throws Exception {
        UUID cnId = UUID.randomUUID();
        when(creditNoteService.getCreditNoteById(cnId)).thenThrow(new CreditNoteNotFoundException("Not found: " + cnId));

        mockMvc.perform(get("/api/v1/bsm/credit-notes/{id}", cnId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void applyCreditNote_returns200() throws Exception {
        UUID cnId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CreditNote cn = creditNote(cnId, tenantId, CreditNoteStatus.APPLIED);
        CreditNoteResponse response = creditNoteResponse(cnId, tenantId, CreditNoteStatus.APPLIED);

        when(creditNoteService.applyCreditNote(cnId)).thenReturn(cn);
        when(creditNoteApiMapper.toResponse(cn)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/credit-notes/{id}/apply", cnId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }

    @Test
    void voidCreditNote_returns200() throws Exception {
        UUID cnId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CreditNote cn = creditNote(cnId, tenantId, CreditNoteStatus.VOID);
        CreditNoteResponse response = creditNoteResponse(cnId, tenantId, CreditNoteStatus.VOID);

        when(creditNoteService.voidCreditNote(cnId)).thenReturn(cn);
        when(creditNoteApiMapper.toResponse(cn)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/credit-notes/{id}/void", cnId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("VOID"));
    }

    private CreditNote creditNote(UUID id, UUID tenantId, CreditNoteStatus status) {
        return CreditNote.builder()
            .id(id).tenantId(tenantId).invoiceId(UUID.randomUUID())
            .creditNumber("CN-001").amountMinor(1000L).currency("INR")
            .reason("test").status(status).createdAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }

    private CreditNoteResponse creditNoteResponse(UUID id, UUID tenantId, CreditNoteStatus status) {
        return new CreditNoteResponse(id, tenantId, UUID.randomUUID(), "CN-001",
            1000L, "INR", "test", status, UUID.randomUUID(), Instant.now(), null);
    }
}
