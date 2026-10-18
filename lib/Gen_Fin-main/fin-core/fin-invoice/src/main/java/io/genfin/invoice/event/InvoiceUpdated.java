package io.genfin.invoice.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.invoice.id.InvoiceId;

public record InvoiceUpdated(EventMetadata metadata, InvoiceId invoiceId, int version)
    implements DomainEvent {}
