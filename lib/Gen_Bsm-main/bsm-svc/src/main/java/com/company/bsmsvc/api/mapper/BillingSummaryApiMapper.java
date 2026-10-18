package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.BillingSummary;
import org.springframework.stereotype.Component;

@Component
public class BillingSummaryApiMapper {

    public BillingSummary toResponse(com.company.bsmsvc.domain.model.BillingSummary domain) {
        BillingSummary dto = new BillingSummary();
        dto.totalInvoices = domain.totalInvoices;
        dto.openInvoices = domain.openInvoices;
        dto.paidInvoices = domain.paidInvoices;
        dto.voidInvoices = domain.voidInvoices;
        dto.totalInvoiceAmountMinor = domain.totalInvoiceAmountMinor;
        dto.totalPaidAmountMinor = domain.totalPaidAmountMinor;
        dto.totalCreditAmountMinor = domain.totalCreditAmountMinor;
        dto.outstandingAmountMinor = domain.outstandingAmountMinor;
        dto.recentInvoices = domain.recentInvoices == null ? null : domain.recentInvoices.stream()
            .map(i -> {
                BillingSummary.InvoiceSummary is = new BillingSummary.InvoiceSummary();
                is.id = i.id;
                is.invoiceNumber = i.invoiceNumber;
                is.amountDue = i.amountDue;
                is.amountPaid = i.amountPaid;
                is.status = i.status;
                is.createdAt = i.createdAt;
                return is;
            }).toList();
        dto.recentCredits = domain.recentCredits == null ? null : domain.recentCredits.stream()
            .map(c -> {
                BillingSummary.CreditSummary cs = new BillingSummary.CreditSummary();
                cs.id = c.id;
                cs.creditNumber = c.creditNumber;
                cs.amountMinor = c.amountMinor;
                cs.status = c.status;
                cs.createdAt = c.createdAt;
                return cs;
            }).toList();
        return dto;
    }
}
