package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.InvoiceCreatedMessage;

/**
 * Publishes an invoice-created event. Implementations decide the transport (message broker,
 * outbox table, log). Implementations must be thread-safe/stateless.
 */
public interface InvoiceEventPublisher {
    void publishInvoiceCreated(InvoiceCreatedMessage message);
}
