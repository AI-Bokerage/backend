package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.BillingDashboardService;
import com.company.bsmsvc.domain.model.BillingSummary;
import com.company.bsmsvc.infrastructure.security.BsmTenantScopeEnforcer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class BillingDashboardServiceImpl implements BillingDashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final BsmTenantScopeEnforcer tenantScopeEnforcer;

    public BillingDashboardServiceImpl(JdbcTemplate jdbcTemplate,
                                       BsmTenantScopeEnforcer tenantScopeEnforcer) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopeEnforcer = tenantScopeEnforcer;
    }

    @Override
    public BillingSummary getSummary(UUID tenantId, int recentLimit) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        BillingSummary s = new BillingSummary();

        s.totalInvoices = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM platform_invoices WHERE tenant_id = ?", Long.class, tenantId);
        s.openInvoices = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM platform_invoices WHERE tenant_id = ? AND status = 'OPEN'", Long.class, tenantId);
        s.paidInvoices = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM platform_invoices WHERE tenant_id = ? AND status = 'PAID'", Long.class, tenantId);
        s.voidInvoices = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM platform_invoices WHERE tenant_id = ? AND status = 'VOID'", Long.class, tenantId);

        s.totalInvoiceAmountMinor = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount_due), 0) FROM platform_invoices WHERE tenant_id = ?", Long.class, tenantId);
        s.totalPaidAmountMinor = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount_paid), 0) FROM platform_invoices WHERE tenant_id = ?", Long.class, tenantId);
        s.totalCreditAmountMinor = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount_minor), 0) FROM credit_notes WHERE tenant_id = ? AND status NOT IN ('VOID')",
            Long.class, tenantId);

        // Outstanding = sum of unpaid balance on open invoices only; never negative.
        s.outstandingAmountMinor = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount_due - amount_paid), 0) FROM platform_invoices WHERE tenant_id = ? AND status IN ('OPEN', 'PARTIALLY_PAID')",
            Long.class, tenantId);

        List<BillingSummary.InvoiceSummary> recentInvoices = jdbcTemplate.query(
            "SELECT id, invoice_number, amount_due, amount_paid, status, created_at FROM platform_invoices WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
            (rs, rowNum) -> {
                BillingSummary.InvoiceSummary is = new BillingSummary.InvoiceSummary();
                is.id = UUID.fromString(rs.getString("id"));
                is.invoiceNumber = rs.getString("invoice_number");
                is.amountDue = rs.getLong("amount_due");
                is.amountPaid = rs.getLong("amount_paid");
                is.status = rs.getString("status");
                is.createdAt = rs.getTimestamp("created_at").toInstant();
                return is;
            },
            tenantId, recentLimit);
        s.recentInvoices = recentInvoices;

        List<BillingSummary.CreditSummary> recentCredits = jdbcTemplate.query(
            "SELECT id, credit_number, amount_minor, status, created_at FROM credit_notes WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
            (rs, rowNum) -> {
                BillingSummary.CreditSummary cs = new BillingSummary.CreditSummary();
                cs.id = UUID.fromString(rs.getString("id"));
                cs.creditNumber = rs.getString("credit_number");
                cs.amountMinor = rs.getLong("amount_minor");
                cs.status = rs.getString("status");
                cs.createdAt = rs.getTimestamp("created_at").toInstant();
                return cs;
            },
            tenantId, recentLimit);
        s.recentCredits = recentCredits;

        return s;
    }
}
