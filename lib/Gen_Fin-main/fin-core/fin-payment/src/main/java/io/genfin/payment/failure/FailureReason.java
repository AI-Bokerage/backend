package io.genfin.payment.failure;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * A fully generic description of why a payment attempt failed — never a provider's own error
 * code/shape.
 */
public record FailureReason(
    FailureCategory category,
    String message,
    RetryRecommendation retryRecommendation,
    RecoveryAction recoveryAction)
    implements ValueObject {

  public FailureReason {
    Validate.notNull(category, "category must not be null.");
    Validate.notBlank(message, "message must not be blank.");
    Validate.notNull(retryRecommendation, "retryRecommendation must not be null.");
    Validate.notNull(recoveryAction, "recoveryAction must not be null.");
  }

  public static FailureReason of(FailureCategory category, String message) {
    return new FailureReason(
        category, message, RetryRecommendation.DO_NOT_RETRY, RecoveryAction.NONE);
  }
}
