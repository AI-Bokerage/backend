package io.genfin.dunning.failure;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.port.failure.FailurePolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultFailureClassifierTest {

  private static final FailurePolicy SCHEME =
      FailurePolicies.of(
          List.of(
              FailureClassificationRule.of(
                  StandardFailureCategory.TEMPORARY, StandardFailureDisposition.RETRY),
              FailureClassificationRule.of(
                  StandardFailureCategory.PERMANENT, StandardFailureDisposition.WRITE_OFF),
              FailureClassificationRule.of(
                  StandardFailureCategory.FRAUD, StandardFailureDisposition.ESCALATE)));

  @Test
  void aTemporaryFailureClassifiesToRetry() {
    FailureReason reason = FailureReason.of("gateway_timeout", StandardFailureCategory.TEMPORARY);

    FailureClassification classification = FailureClassifiers.standard().classify(reason, SCHEME);

    assertThat(classification.classified()).isTrue();
    assertThat(classification.dispositionIfClassified()).contains(StandardFailureDisposition.RETRY);
  }

  @Test
  void aFraudFailureClassifiesToEscalate() {
    FailureReason reason = FailureReason.of("suspected_fraud", StandardFailureCategory.FRAUD);

    FailureClassification classification = FailureClassifiers.standard().classify(reason, SCHEME);

    assertThat(classification.dispositionIfClassified())
        .contains(StandardFailureDisposition.ESCALATE);
  }

  @Test
  void aCategoryWithNoRuleIsUnclassified() {
    FailureReason reason = FailureReason.of("chargeback", StandardFailureCategory.CUSTOMER);

    FailureClassification classification = FailureClassifiers.standard().classify(reason, SCHEME);

    assertThat(classification.classified()).isFalse();
    assertThat(classification.dispositionIfClassified()).isEmpty();
  }

  @Test
  void anEmptyPolicyNeverClassifies() {
    FailureReason reason = FailureReason.of("gateway_timeout", StandardFailureCategory.TEMPORARY);

    FailureClassification classification =
        FailureClassifiers.standard().classify(reason, FailurePolicies.standard());

    assertThat(classification.classified()).isFalse();
  }
}
