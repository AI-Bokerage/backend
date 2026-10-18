package io.genfin.dunning.retry;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.List;
import java.util.Optional;

/**
 * The full, ordered set of {@link RetryDecision}s computed for an obligation - attempts 1..N,
 * terminated by an exhausted decision. Produced by a {@code RetryCalculator}; never executed by
 * fin-dunning.
 */
public record RetryPlan(List<RetryDecision> decisions) implements ValueObject {

  public RetryPlan {
    decisions = List.copyOf(decisions);
    Validate.required(!decisions.isEmpty(), "decisions must not be empty.");
  }

  public static RetryPlan of(List<RetryDecision> decisions) {
    return new RetryPlan(decisions);
  }

  /** True once the last decision in the plan reports exhaustion. */
  public boolean isExhausted() {
    return decisions.get(decisions.size() - 1).exhausted();
  }

  public Optional<RetryDecision> decisionForAttempt(int attemptNumber) {
    return decisions.stream()
        .filter(decision -> decision.attemptNumber() == attemptNumber)
        .findFirst();
  }

  public long scheduledAttemptCount() {
    return decisions.stream().filter(decision -> !decision.exhausted()).count();
  }
}
