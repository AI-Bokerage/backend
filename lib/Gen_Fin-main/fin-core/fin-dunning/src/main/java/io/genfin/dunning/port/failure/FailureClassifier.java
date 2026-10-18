package io.genfin.dunning.port.failure;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.failure.FailureClassification;
import io.genfin.dunning.failure.FailureReason;

/**
 * SPI deciding how a payment-not-received event's {@link FailureReason} should be handled - retry,
 * escalate, or whatever else an application's {@link FailurePolicy} classification scheme defines -
 * so that decision can feed the Retry Engine and Escalation Engine. Decides only - never retries,
 * escalates, writes off, or performs any other action itself.
 */
@FunctionalInterface
public interface FailureClassifier extends Extension {

  /**
   * Classifies one failure reason against the given policy.
   *
   * @param reason the failure signal to classify.
   * @param policy the resolved classification scheme to classify against.
   */
  FailureClassification classify(FailureReason reason, FailurePolicy policy);
}
