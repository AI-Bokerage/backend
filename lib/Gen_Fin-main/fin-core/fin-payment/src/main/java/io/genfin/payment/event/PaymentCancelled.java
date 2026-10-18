package io.genfin.payment.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.payment.id.PaymentId;

public record PaymentCancelled(EventMetadata metadata, PaymentId paymentId, String reason)
    implements DomainEvent {}
