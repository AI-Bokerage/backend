package io.genfin.payment.failure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailureModelTest {

  @Test
  void ofBuildsANonRetryableNoActionReason() {
    FailureReason reason =
        FailureReason.of(FailureCategory.CARD_DECLINED, "Card declined by issuer.");

    assertThat(reason.retryRecommendation()).isEqualTo(RetryRecommendation.DO_NOT_RETRY);
    assertThat(reason.recoveryAction()).isEqualTo(RecoveryAction.NONE);
  }

  @Test
  void fullyCustomFailureReasonCarriesRecoveryGuidance() {
    FailureReason reason =
        new FailureReason(
            FailureCategory.INSUFFICIENT_FUNDS,
            "Insufficient funds.",
            RetryRecommendation.RETRY_WITH_DIFFERENT_METHOD,
            RecoveryAction.UPDATE_PAYMENT_METHOD);

    assertThat(reason.retryRecommendation())
        .isEqualTo(RetryRecommendation.RETRY_WITH_DIFFERENT_METHOD);
    assertThat(reason.recoveryAction()).isEqualTo(RecoveryAction.UPDATE_PAYMENT_METHOD);
  }
}
