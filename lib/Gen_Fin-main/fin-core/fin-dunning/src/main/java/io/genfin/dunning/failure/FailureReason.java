package io.genfin.dunning.failure;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * One specific signal explaining why a payment-not-received event occurred (what an application
 * might call "insufficient funds", "card expired", "gateway timeout", or "suspected fraud"),
 * already tagged with the broad {@link FailureCategory} it falls into. fin-dunning never assigns
 * this category itself - an application maps its own provider/gateway-specific codes onto a {@link
 * FailureCategory} before handing the reason to a {@code FailureClassifier}.
 */
public record FailureReason(String code, FailureCategory category, String description)
    implements ValueObject {

  public FailureReason {
    Validate.notBlank(code, "code must not be blank.");
    Validate.notNull(category, "category must not be null.");
  }

  public static FailureReason of(String code, FailureCategory category) {
    return new FailureReason(code, category, null);
  }

  public static FailureReason of(String code, FailureCategory category, String description) {
    return new FailureReason(code, category, description);
  }
}
