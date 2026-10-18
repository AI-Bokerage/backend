package com.company.bsmsvc.storage;

import com.company.bsmsvc.domain.model.PlatformInvoice;

public interface InvoiceDocumentStoragePort {
    String uploadInvoicePdf(PlatformInvoice invoice, byte[] pdfContent, String contentType);
}
