package io.genfin.refund.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.refund.id.RefundId;

public record RefundCancelled(EventMetadata metadata, RefundId refundId, String reason)
    implements DomainEvent {}
