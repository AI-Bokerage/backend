package io.genfin.dunning.escalation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.port.escalation.EscalationPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultEscalationStrategyTest {

  private static final EscalationPolicy LADDER =
      EscalationPolicies.of(
          List.of(
              EscalationRule.of(
                  2, StandardEscalationLevel.LEVEL_1, StandardEscalationAction.NOTIFY_MANAGER),
              EscalationRule.of(
                  4, StandardEscalationLevel.LEVEL_2, StandardEscalationAction.SUSPEND_SERVICE),
              EscalationRule.of(
                  6, StandardEscalationLevel.LEVEL_3, StandardEscalationAction.WRITE_OFF)));

  @Test
  void belowEveryThresholdDecidesNoEscalation() {
    EscalationDecision decision = EscalationStrategies.standard().decide(1, LADDER);

    assertThat(decision.escalated()).isFalse();
    assertThat(decision.levelIfEscalated()).isEmpty();
    assertThat(decision.actionIfEscalated()).isEmpty();
  }

  @Test
  void atAThresholdEscalatesToThatRungsLevelAndAction() {
    EscalationDecision decision = EscalationStrategies.standard().decide(2, LADDER);

    assertThat(decision.escalated()).isTrue();
    assertThat(decision.level()).isEqualTo(StandardEscalationLevel.LEVEL_1);
    assertThat(decision.action()).isEqualTo(StandardEscalationAction.NOTIFY_MANAGER);
  }

  @Test
  void betweenTwoThresholdsStaysAtTheLowerRung() {
    EscalationDecision decision = EscalationStrategies.standard().decide(3, LADDER);

    assertThat(decision.level()).isEqualTo(StandardEscalationLevel.LEVEL_1);
  }

  @Test
  void pastTheHighestThresholdEscalatesToTheHighestRung() {
    EscalationDecision decision = EscalationStrategies.standard().decide(9, LADDER);

    assertThat(decision.level()).isEqualTo(StandardEscalationLevel.LEVEL_3);
    assertThat(decision.action()).isEqualTo(StandardEscalationAction.WRITE_OFF);
  }

  @Test
  void anEmptyPolicyNeverEscalates() {
    EscalationDecision decision =
        EscalationStrategies.standard().decide(100, EscalationPolicies.standard());

    assertThat(decision.escalated()).isFalse();
  }
}
