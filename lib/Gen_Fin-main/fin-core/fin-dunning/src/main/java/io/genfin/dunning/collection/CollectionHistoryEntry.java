package io.genfin.dunning.collection;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Instant;

/** A single, immutable record of a stage transition recorded against a {@code DunningCase}. */
public record CollectionHistoryEntry(CollectionStage stage, Instant occurredAt, String note)
    implements ValueObject {

  public CollectionHistoryEntry {
    Validate.notNull(stage, "stage must not be null.");
    Validate.notNull(occurredAt, "occurredAt must not be null.");
  }
}
