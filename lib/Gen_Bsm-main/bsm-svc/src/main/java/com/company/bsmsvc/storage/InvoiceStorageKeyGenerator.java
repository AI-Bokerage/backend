package com.company.bsmsvc.storage;

import com.company.bsmsvc.domain.model.PlatformInvoice;

public interface InvoiceStorageKeyGenerator {
    String generateKey(PlatformInvoice invoice);
}
