package io.genfin.dunning.collection;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.ArrayList;
import java.util.List;

/** The append-only sequence of {@link CollectionHistoryEntry} recorded against a DunningCase. */
public record CollectionHistory(List<CollectionHistoryEntry> entries) implements ValueObject {

  public CollectionHistory {
    entries = List.copyOf(entries);
  }

  public static CollectionHistory empty() {
    return new CollectionHistory(List.of());
  }

  public CollectionHistory append(CollectionHistoryEntry entry) {
    Validate.notNull(entry, "entry must not be null.");
    List<CollectionHistoryEntry> updated = new ArrayList<>(entries);
    updated.add(entry);
    return new CollectionHistory(updated);
  }

  public long countAt(CollectionStage stage) {
    return entries.stream().filter(entry -> entry.stage() == stage).count();
  }
}
