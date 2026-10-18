package io.genfin.invoice.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.invoice.id.InvoiceId;

public record InvoiceCancelled(EventMetadata metadata, InvoiceId invoiceId, String reason)
    implements DomainEvent {}
