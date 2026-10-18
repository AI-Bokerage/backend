package io.genfin.dunning.failure;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.Optional;

/**
 * The outcome of asking a {@code FailureClassifier} how a payment-not-received event's {@link
 * FailureReason} should be handled: either the matching {@link FailureDisposition} from an
 * application's classification scheme, or no match (an incomplete scheme - a category with no
 * rule). Data only - fin-dunning never retries, escalates, writes off, or reviews anything itself;
 * the Retry Engine and Escalation Engine decide their own plans from this label.
 */
public record FailureClassification(
    FailureReason reason, FailureDisposition disposition, boolean classified)
    implements ValueObject {

  public FailureClassification {
    Validate.notNull(reason, "reason must not be null.");
    Validate.required(
        classified == (disposition != null),
        "disposition must be present exactly when classified.");
  }

  public static FailureClassification classified(
      FailureReason reason, FailureDisposition disposition) {
    return new FailureClassification(
        reason, Validate.notNull(disposition, "disposition must not be null."), true);
  }

  public static FailureClassification unclassified(FailureReason reason) {
    return new FailureClassification(reason, null, false);
  }

  public Optional<FailureDisposition> dispositionIfClassified() {
    return Optional.ofNullable(disposition);
  }
}
