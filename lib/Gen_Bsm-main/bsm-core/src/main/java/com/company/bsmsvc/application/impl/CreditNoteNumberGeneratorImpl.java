package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.CreditNoteNumberGenerator;
import java.time.Year;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CreditNoteNumberGeneratorImpl implements CreditNoteNumberGenerator {

    private static final java.util.concurrent.atomic.AtomicLong COUNTER = new java.util.concurrent.atomic.AtomicLong(1);

    @Override
    public String generateCreditNumber(UUID tenantId) {
        long seq = COUNTER.getAndIncrement();
        return String.format("CN-%d-%06d", Year.now().getValue(), seq);
    }
}
