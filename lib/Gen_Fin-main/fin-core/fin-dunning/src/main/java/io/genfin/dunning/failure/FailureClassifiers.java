package io.genfin.dunning.failure;

import io.genfin.dunning.internal.failure.DefaultFailureClassifier;
import io.genfin.dunning.port.failure.FailureClassifier;

/**
 * Factory for {@link FailureClassifier} instances. For anything other than the standard
 * first-matching-category lookup, an application implements {@link FailureClassifier} directly.
 */
public final class FailureClassifiers {

  private static final FailureClassifier STANDARD = new DefaultFailureClassifier();

  private FailureClassifiers() {}

  /** Picks the first {@code FailureClassificationRule} whose category matches the reason's. */
  public static FailureClassifier standard() {
    return STANDARD;
  }
}
