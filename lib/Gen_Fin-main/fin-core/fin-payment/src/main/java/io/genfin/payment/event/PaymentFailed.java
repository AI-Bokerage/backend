package io.genfin.payment.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.id.PaymentId;

public record PaymentFailed(EventMetadata metadata, PaymentId paymentId, FailureReason reason)
    implements DomainEvent {}
