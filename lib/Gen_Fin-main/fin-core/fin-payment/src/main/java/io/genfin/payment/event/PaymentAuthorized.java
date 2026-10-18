package io.genfin.payment.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.money.money.Money;
import io.genfin.payment.id.PaymentId;

public record PaymentAuthorized(EventMetadata metadata, PaymentId paymentId, Money amount)
    implements DomainEvent {}
