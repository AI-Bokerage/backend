package io.genfin.providerapi.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.providerapi.descriptor.ProviderId;

public record ProviderRegistered(EventMetadata metadata, ProviderId providerId)
    implements DomainEvent {}
