package com.company.bsmsvc.pdf;

import com.company.bsmsvc.domain.model.PlatformInvoice;

public interface InvoicePdfGenerator {
    byte[] generatePdf(PlatformInvoice invoice);
}
