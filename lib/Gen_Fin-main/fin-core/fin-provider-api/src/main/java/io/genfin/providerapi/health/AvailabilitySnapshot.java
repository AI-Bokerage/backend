package io.genfin.providerapi.health;

import io.genfin.api.domain.ValueObject;
import java.time.Instant;

public record AvailabilitySnapshot(double successRate, Instant windowStart, Instant windowEnd)
    implements ValueObject {}
