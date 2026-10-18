package io.genfin.dunning.collection;

import io.genfin.api.domain.ValueObject;

/**
 * A point-in-time summary of a {@code DunningCase}'s progress. Mirrors {@code
 * io.genfin.ledger.ledger.Ledger.Summary}.
 */
public record CollectionSummary(
    CollectionStage currentStage,
    long reminderCount,
    long retryCount,
    long escalationCount,
    int historyEntryCount)
    implements ValueObject {

  public static CollectionSummary of(CollectionStage currentStage, CollectionHistory history) {
    return new CollectionSummary(
        currentStage,
        history.countAt(CollectionStage.REMINDING),
        history.countAt(CollectionStage.RETRYING),
        history.countAt(CollectionStage.ESCALATING),
        history.entries().size());
  }
}
