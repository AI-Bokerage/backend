package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.model.BillingSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.infrastructure.security.BsmTenantScopeEnforcer;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class BillingDashboardServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock private BsmTenantScopeEnforcer tenantScopeEnforcer;

    @InjectMocks
    private BillingDashboardServiceImpl service;


    @org.junit.jupiter.api.BeforeEach
    void setupEnforcer() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSummary_returnsCorrectAggregates() {
        UUID tenantId = UUID.randomUUID();

        // 8 queryForObject calls: totalInvoices, openInvoices, paidInvoices, voidInvoices,
        // totalInvoiceAmount, totalPaidAmount, totalCreditAmount, outstandingAmount
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(tenantId)))
            .thenReturn(10L, 3L, 5L, 2L, 100000L, 50000L, 10000L, 40000L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(tenantId), any(int.class)))
            .thenReturn(List.of());

        BillingSummary summary = service.getSummary(tenantId, 10);

        assertThat(summary.totalInvoices).isEqualTo(10L);
        assertThat(summary.openInvoices).isEqualTo(3L);
        assertThat(summary.paidInvoices).isEqualTo(5L);
        assertThat(summary.voidInvoices).isEqualTo(2L);
        assertThat(summary.totalInvoiceAmountMinor).isEqualTo(100000L);
        assertThat(summary.totalPaidAmountMinor).isEqualTo(50000L);
        assertThat(summary.totalCreditAmountMinor).isEqualTo(10000L);
        assertThat(summary.outstandingAmountMinor).isEqualTo(40000L);
        assertThat(summary.recentInvoices).isEmpty();
        assertThat(summary.recentCredits).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSummary_outstandingReflectsOnlyUnpaidInvoices() {
        UUID tenantId = UUID.randomUUID();

        // outstanding comes from its own query (open/partially-paid invoices only)
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(tenantId)))
            .thenReturn(5L, 1L, 3L, 1L, 80000L, 30000L, 5000L, 15000L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(tenantId), any(int.class)))
            .thenReturn(List.of());

        BillingSummary summary = service.getSummary(tenantId, 5);

        assertThat(summary.outstandingAmountMinor).isEqualTo(15000L);
    }
}
