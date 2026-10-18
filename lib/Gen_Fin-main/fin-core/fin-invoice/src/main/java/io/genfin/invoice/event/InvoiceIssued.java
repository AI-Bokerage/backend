package io.genfin.invoice.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.invoice.id.InvoiceId;
import io.genfin.invoice.numbering.InvoiceNumber;

public record InvoiceIssued(EventMetadata metadata, InvoiceId invoiceId, InvoiceNumber number)
    implements DomainEvent {}
