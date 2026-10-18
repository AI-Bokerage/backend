package io.genfin.invoice.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.invoice.id.InvoiceId;

public record InvoiceOverdue(EventMetadata metadata, InvoiceId invoiceId) implements DomainEvent {}
