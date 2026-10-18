package io.genfin.dunning.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.validation.ValidationContext;
import io.genfin.dunning.validation.ValidationIssue;
import io.genfin.dunning.validation.ValidationRule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration-sanity checks for a resolved {@link DunningPolicy}: its {@link RetryPolicy}'s
 * maximum retries must not be negative, and its backoff strategy must yield a positive delay for
 * the first attempt. Catches an application-supplied {@code RetryPolicy}/{@code BackoffStrategy}
 * implementation that skips these invariants - fin-dunning's own record constructors already
 * enforce them for its built-in configuration types, this rule is the defensive backstop for any
 * other SPI implementation.
 */
public final class DunningPolicyValidationRule implements ValidationRule {

  private static final String RULE_CODE = "DUNNING_POLICY";

  @Override
  public List<ValidationIssue> apply(ValidationContext context) {
    DunningPolicy policy = context.dunningPolicy();
    if (policy == null) {
      return List.of();
    }

    List<ValidationIssue> issues = new ArrayList<>();
    RetryPolicy retryPolicy = policy.schedulePolicy().retryPolicy();

    if (retryPolicy.maxRetries() < 0) {
      issues.add(ValidationIssue.of(RULE_CODE, "maxRetries must not be negative.", Severity.ERROR));
    }

    Duration firstAttemptDelay = retryPolicy.backoffStrategy().nextDelay(1);
    if (firstAttemptDelay == null || firstAttemptDelay.isNegative() || firstAttemptDelay.isZero()) {
      issues.add(
          ValidationIssue.of(
              RULE_CODE, "backoffStrategy's base interval must be positive.", Severity.ERROR));
    }

    if (policy.stages().isEmpty()) {
      issues.add(ValidationIssue.of(RULE_CODE, "stages must not be empty.", Severity.ERROR));
    }

    return issues;
  }
}
