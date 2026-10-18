package io.genfin.dunning.failure;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * One entry of an application's classification scheme: whenever a {@link FailureReason}'s {@link
 * FailureCategory} matches {@code category}, the resolved {@link FailureDisposition} applies.
 * fin-dunning never decides which category maps to which disposition - that mapping always comes
 * from an application's own {@code DunningPolicy}.
 */
public record FailureClassificationRule(FailureCategory category, FailureDisposition disposition)
    implements ValueObject {

  public FailureClassificationRule {
    Validate.notNull(category, "category must not be null.");
    Validate.notNull(disposition, "disposition must not be null.");
  }

  public static FailureClassificationRule of(
      FailureCategory category, FailureDisposition disposition) {
    return new FailureClassificationRule(category, disposition);
  }
}
