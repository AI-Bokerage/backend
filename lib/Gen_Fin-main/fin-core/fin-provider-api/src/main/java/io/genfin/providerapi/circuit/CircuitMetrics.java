package io.genfin.providerapi.circuit;

import io.genfin.api.domain.ValueObject;
import java.time.Instant;

public record CircuitMetrics(long successCount, long failureCount, Instant lastFailureAt)
    implements ValueObject {}
