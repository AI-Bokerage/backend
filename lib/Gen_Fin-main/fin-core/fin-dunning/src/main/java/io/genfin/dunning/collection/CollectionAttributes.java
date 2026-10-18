package io.genfin.dunning.collection;

import io.genfin.api.domain.ValueObject;

/**
 * Structural flags governing how a {@code DunningCase} participates in collection - never a
 * schedule, channel, or escalation action itself. Mirrors {@code
 * io.genfin.ledger.account.AccountAttributes}.
 */
public record CollectionAttributes(
    boolean disputed, boolean manualReviewRequired, boolean suspended) implements ValueObject {

  /** A normal case, free to progress through its plan automatically. */
  public static CollectionAttributes standard() {
    return new CollectionAttributes(false, false, false);
  }

  public CollectionAttributes withDisputed(boolean value) {
    return new CollectionAttributes(value, manualReviewRequired, suspended);
  }

  public CollectionAttributes withManualReviewRequired(boolean value) {
    return new CollectionAttributes(disputed, value, suspended);
  }

  public CollectionAttributes withSuspended(boolean value) {
    return new CollectionAttributes(disputed, manualReviewRequired, value);
  }
}
