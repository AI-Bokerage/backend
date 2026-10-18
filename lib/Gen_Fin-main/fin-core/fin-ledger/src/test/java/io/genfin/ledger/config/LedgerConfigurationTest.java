package io.genfin.ledger.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LedgerConfigurationTest {

  @Test
  void standardConfigurationComposesEveryDefaultExtension() {
    LedgerConfiguration configuration = LedgerConfigurations.standard();

    assertThat(configuration.lifecycleProvider()).isNotNull();
    assertThat(configuration.accountTypeRegistry()).isNotNull();
    assertThat(configuration.journalBuilderProvider()).isNotNull();
    assertThat(configuration.reversalPolicy()).isNotNull();
    assertThat(configuration.reverseJournal()).isNotNull();
    assertThat(configuration.postingConfiguration().policy()).isNotNull();
    assertThat(configuration.postingConfiguration().validator()).isNotNull();
    assertThat(configuration.postingConfiguration().ruleRegistry()).isNotNull();
    assertThat(configuration.postingConfiguration().ruleResolver()).isNotNull();
    assertThat(configuration.postingConfiguration().engine()).isNotNull();
    assertThat(configuration.balanceConfiguration().policy()).isNotNull();
    assertThat(configuration.balanceConfiguration().calculator()).isNotNull();
    assertThat(configuration.periodConfiguration().policy()).isNotNull();
    assertThat(configuration.periodConfiguration().calculator()).isNotNull();
    assertThat(configuration.reportingConfiguration().trialBalanceCalculator()).isNotNull();
    assertThat(configuration.reportingConfiguration().formatter()).isNotNull();
    assertThat(configuration.validationConfiguration().journalValidator()).isNotNull();
  }

  @Test
  void builderRejectsMissingCollaborators() {
    assertThatThrownBy(() -> LedgerConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> PostingConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> BalanceConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> PeriodConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> ReportingConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> ValidationConfiguration.builder().build())
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void postingPolicyStartsEmptySoConsumersMustRegisterTheirOwnAccountsAndRules() {
    LedgerConfiguration configuration = LedgerConfigurations.standard();

    assertThat(configuration.postingConfiguration().ruleRegistry().findAll()).isEmpty();
  }
}
