package io.genfin.providerapi.health;

import io.genfin.api.domain.ValueObject;
import java.time.Duration;

public record LatencySnapshot(Duration p50, Duration p95, Duration p99) implements ValueObject {}
