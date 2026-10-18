package io.genfin.providerapi.health;

import io.genfin.api.domain.ValueObject;
import java.time.Instant;

public record ProviderHealth(
    HealthStatus status,
    LatencySnapshot latency,
    AvailabilitySnapshot availability,
    Instant checkedAt)
    implements ValueObject {

  public boolean isUsable() {
    return status != HealthStatus.DOWN;
  }
}
