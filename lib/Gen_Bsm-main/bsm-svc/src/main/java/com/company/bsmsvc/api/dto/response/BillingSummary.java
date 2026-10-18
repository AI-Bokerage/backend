package com.company.bsmsvc.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class BillingSummary {
    public long totalInvoices;
    public long openInvoices;
    public long paidInvoices;
    public long voidInvoices;
    public long totalInvoiceAmountMinor;
    public long totalPaidAmountMinor;
    public long totalCreditAmountMinor;
    public long outstandingAmountMinor;
    public List<InvoiceSummary> recentInvoices;
    public List<CreditSummary> recentCredits;

    public static class InvoiceSummary {
        public UUID id;
        public String invoiceNumber;
        public long amountDue;
        public long amountPaid;
        public String status;
        public Instant createdAt;
    }

    public static class CreditSummary {
        public UUID id;
        public String creditNumber;
        public long amountMinor;
        public String status;
        public Instant createdAt;
    }
}
