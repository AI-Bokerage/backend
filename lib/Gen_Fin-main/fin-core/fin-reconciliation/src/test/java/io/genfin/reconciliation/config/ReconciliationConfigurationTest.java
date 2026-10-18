package io.genfin.reconciliation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReconciliationConfigurationTest {

  @Test
  void standardConfigurationComposesEveryDefaultExtension() {
    ReconciliationConfiguration configuration = ReconciliationConfigurations.standard();

    assertThat(configuration.lifecycleProvider()).isNotNull();
    assertThat(configuration.comparisonPolicy()).isNotNull();
    assertThat(configuration.discrepancyDetector()).isNotNull();
    assertThat(configuration.discrepancyPolicy()).isNotNull();
    assertThat(configuration.toleranceConfiguration().calculator()).isNotNull();
    assertThat(configuration.toleranceConfiguration().customRules()).isNotNull();
    assertThat(configuration.matchingConfiguration().engine()).isNotNull();
    assertThat(configuration.matchingConfiguration().policy()).isNotNull();
    assertThat(configuration.matchingConfiguration().strategies()).isNotEmpty();
    assertThat(configuration.ruleConfiguration().engine()).isNotNull();
    assertThat(configuration.ruleConfiguration().rules()).isNotEmpty();
    assertThat(configuration.reportingConfiguration().summaryCalculator()).isNotNull();
    assertThat(configuration.reportingConfiguration().formatter()).isNotNull();
  }

  @Test
  void builderRejectsMissingCollaborators() {
    assertThatThrownBy(() -> ReconciliationConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> ToleranceConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> MatchingConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> RuleConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> ReportingConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
  }
}
