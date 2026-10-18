package io.genfin.invoice.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.invoice.id.InvoiceId;
import io.genfin.money.money.Money;

public record InvoicePaid(EventMetadata metadata, InvoiceId invoiceId, Money amountPaid)
    implements DomainEvent {}
