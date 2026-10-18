package io.genfin.invoice.invoice;

import io.genfin.api.domain.ValueObject;
import java.time.Instant;

public record AuditInfo(Instant createdAt, Instant updatedAt, String createdBy, String updatedBy)
    implements ValueObject {

  public AuditInfo touched(Instant now, String actor) {
    return new AuditInfo(createdAt, now, createdBy, actor);
  }
}
