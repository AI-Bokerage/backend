package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.InvoiceNumberGenerator;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InvoiceNumberGeneratorImpl implements InvoiceNumberGenerator {

    @Override
    public String generateInvoiceNumber(UUID tenantId, UUID subscriptionId) {
        int year = Year.now(ZoneOffset.UTC).getValue();
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return String.format("INV-%d-%s", year, unique);
    }
}
