package io.genfin.dunning.retry;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.port.retry.RetryStrategy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives a {@link RetryStrategy} across attempts 1..N to produce the full {@link RetryPlan} for an
 * obligation - the Retry Engine's entry point. Plans only; never executes a retry.
 */
public final class RetryCalculator {

  private final RetryStrategy strategy;

  public RetryCalculator(RetryStrategy strategy) {
    this.strategy = Validate.notNull(strategy, "strategy must not be null.");
  }

  /**
   * Plans every retry attempt for an obligation, from attempt 1 up to exhaustion.
   *
   * @param policy the resolved policy to plan against.
   * @param from the reference instant attempt delays are measured from.
   */
  public RetryPlan plan(RetryPolicy policy, Instant from) {
    Validate.notNull(policy, "policy must not be null.");
    Validate.notNull(from, "from must not be null.");

    List<RetryDecision> decisions = new ArrayList<>();
    // ponytail: bounded by policy.maxRetries() + 1 regardless of strategy behavior, so a
    // misbehaving custom RetryStrategy that never reports exhaustion cannot loop forever.
    for (int attemptNumber = 1; attemptNumber <= policy.maxRetries() + 1; attemptNumber++) {
      RetryDecision decision = strategy.decide(attemptNumber, from, policy);
      decisions.add(decision);
      if (decision.exhausted()) {
        break;
      }
    }
    return RetryPlan.of(decisions);
  }
}
