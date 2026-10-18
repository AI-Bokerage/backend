package io.genfin.payment.gateway;

import io.genfin.api.domain.ValueObject;
import java.time.Instant;

public record GatewayHealth(boolean healthy, String statusMessage, Instant checkedAt)
    implements ValueObject {

  public static GatewayHealth up(Instant checkedAt) {
    return new GatewayHealth(true, "OK", checkedAt);
  }

  public static GatewayHealth down(String statusMessage, Instant checkedAt) {
    return new GatewayHealth(false, statusMessage, checkedAt);
  }
}
