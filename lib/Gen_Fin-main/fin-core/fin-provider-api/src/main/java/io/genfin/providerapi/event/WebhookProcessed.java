package io.genfin.providerapi.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;

public record WebhookProcessed(EventMetadata metadata, String eventId, String eventType)
    implements DomainEvent {}
