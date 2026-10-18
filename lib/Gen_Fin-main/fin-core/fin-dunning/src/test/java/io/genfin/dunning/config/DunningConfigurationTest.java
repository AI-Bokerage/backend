package io.genfin.dunning.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.failure.FailurePolicies;
import io.genfin.dunning.policy.DunningPolicies;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the immutable, multi-piece {@link DunningConfiguration} builder and its {@link
 * DunningConfigurations#standard()} default.
 */
class DunningConfigurationTest {

  @Test
  void standardConfigurationCarriesAFullyDefaultedButNonNullPolicySet() {
    DunningConfiguration configuration = DunningConfigurations.standard();

    assertThat(configuration.dunningPolicy()).isNotNull();
    assertThat(configuration.lifecycleProvider()).isNotNull();
    assertThat(configuration.collectionRuleEngine()).isNotNull();
    assertThat(configuration.failurePolicy()).isNotNull();
    assertThat(configuration.notificationStrategy()).isNotNull();
    assertThat(configuration.backoffConfiguration()).isNotNull();
    assertThat(configuration.calendarConfiguration()).isNotNull();
    assertThat(configuration.retryConfiguration()).isNotNull();
    assertThat(configuration.scheduleConfiguration()).isNotNull();
    assertThat(configuration.reminderConfiguration()).isNotNull();
    assertThat(configuration.escalationConfiguration()).isNotNull();
    assertThat(configuration.validationConfiguration()).isNotNull();
  }

  @Test
  void builderHonoursEveryExplicitlySuppliedCollaborator() {
    DunningConfiguration standard = DunningConfigurations.standard();
    var customFailurePolicy = FailurePolicies.standard();

    DunningConfiguration configuration =
        DunningConfiguration.builder()
            .dunningPolicy(DunningPolicies.standard())
            .lifecycleProvider(standard.lifecycleProvider())
            .collectionRuleEngine(standard.collectionRuleEngine())
            .failurePolicy(customFailurePolicy)
            .notificationStrategy(standard.notificationStrategy())
            .backoffConfiguration(standard.backoffConfiguration())
            .calendarConfiguration(standard.calendarConfiguration())
            .retryConfiguration(standard.retryConfiguration())
            .scheduleConfiguration(standard.scheduleConfiguration())
            .reminderConfiguration(standard.reminderConfiguration())
            .escalationConfiguration(standard.escalationConfiguration())
            .validationConfiguration(standard.validationConfiguration())
            .build();

    assertThat(configuration.failurePolicy()).isEqualTo(customFailurePolicy);
  }

  @Test
  void retryConfigurationDefaultIsNotNull() {
    assertThat(RetryConfiguration.builder().build().retryPolicy()).isNotNull();
  }

  @Test
  void backoffConfigurationDefaultYieldsAPositiveFirstDelay() {
    var backoffStrategy = BackoffConfiguration.builder().build().backoffStrategy();
    assertThat(backoffStrategy.nextDelay(1).isPositive()).isTrue();
  }

  @Test
  void validationConfigurationDefaultValidatesTheStandardPolicy() {
    var validator = ValidationConfiguration.builder().build().dunningValidator();
    var result =
        validator.validate(
            io.genfin.dunning.validation.ValidationContext.of(DunningPolicies.standard()));
    assertThat(result.isValid()).isTrue();
  }
}
