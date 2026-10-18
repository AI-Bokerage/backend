package com.company.bsmsvc.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.mapper.BillingSummaryApiMapper;
import com.company.bsmsvc.application.service.BillingDashboardService;
import com.company.bsmsvc.domain.model.BillingSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BillingDashboardController.class)
@Import({GlobalExceptionHandler.class, BillingSummaryApiMapper.class})
class BillingDashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BillingDashboardService billingDashboardService;

    @Test
    void summary_returns200WithData() throws Exception {
        UUID tenantId = UUID.randomUUID();
        BillingSummary summary = buildSummary(10L, 3L, 6L, 1L, 100000L, 60000L, 5000L);

        when(billingDashboardService.getSummary(eq(tenantId), eq(10))).thenReturn(summary);

        mockMvc.perform(get("/api/v1/bsm/billing/summary")
                .param("tenantId", tenantId.toString())
                .param("recentLimit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalInvoices").value(10))
            .andExpect(jsonPath("$.data.openInvoices").value(3))
            .andExpect(jsonPath("$.data.outstandingAmountMinor").value(35000));
    }

    @Test
    void summary_returns400_whenTenantIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/bsm/billing/summary"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void summary_clampsRecentLimitTo100() throws Exception {
        UUID tenantId = UUID.randomUUID();
        BillingSummary summary = buildSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L);

        when(billingDashboardService.getSummary(eq(tenantId), eq(100))).thenReturn(summary);

        mockMvc.perform(get("/api/v1/bsm/billing/summary")
                .param("tenantId", tenantId.toString())
                .param("recentLimit", "999"))
            .andExpect(status().isOk());
    }

    private BillingSummary buildSummary(long total, long open, long paid, long voided,
                                         long totalAmt, long paidAmt, long creditAmt) {
        BillingSummary s = new BillingSummary();
        s.totalInvoices = total;
        s.openInvoices = open;
        s.paidInvoices = paid;
        s.voidInvoices = voided;
        s.totalInvoiceAmountMinor = totalAmt;
        s.totalPaidAmountMinor = paidAmt;
        s.totalCreditAmountMinor = creditAmt;
        s.outstandingAmountMinor = totalAmt - paidAmt - creditAmt;
        s.recentInvoices = List.of();
        s.recentCredits = List.of();
        return s;
    }
}
