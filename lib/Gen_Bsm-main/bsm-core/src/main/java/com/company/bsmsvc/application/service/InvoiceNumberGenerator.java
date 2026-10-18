package com.company.bsmsvc.application.service;

import java.util.UUID;

/**
 * Generates a human-readable invoice number for a tenant/subscription. The starter supplies a
 * default implementation via {@code BsmSupportAutoConfiguration}, overridable with your own
 * {@code @Bean} (backs off automatically via {@code @ConditionalOnMissingBean}).
 */
public interface InvoiceNumberGenerator {

    String generateInvoiceNumber(UUID tenantId, UUID subscriptionId);
}
