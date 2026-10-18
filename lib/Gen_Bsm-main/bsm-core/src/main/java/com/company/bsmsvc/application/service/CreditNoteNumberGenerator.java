package com.company.bsmsvc.application.service;

import java.util.UUID;

/**
 * Generates a human-readable credit note number for a tenant. Not auto-configured by the
 * starter; supply a {@code @Bean} implementation if you use {@link CreditNoteService}.
 */
public interface CreditNoteNumberGenerator {
    String generateCreditNumber(UUID tenantId);
}
