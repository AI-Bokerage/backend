package io.genfin.refund.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefundConfigurationTest {

  @Test
  void standardConfigurationHasExplicitDefaultsForEveryPolicy() {
    RefundConfiguration configuration = RefundConfigurations.standard();

    assertThat(configuration.lifecycleProvider()).isNotNull();
    assertThat(configuration.approvalPolicy()).isNotNull();
    assertThat(configuration.approvalWorkflow()).isNotNull();
    assertThat(configuration.windowPolicy()).isNotNull();
    assertThat(configuration.maximumRefundPolicy()).isNotNull();
    assertThat(configuration.reasonPolicy()).isNotNull();
    assertThat(configuration.calculator()).isNotNull();
    assertThat(configuration.validator()).isNotNull();
    assertThat(configuration.requestValidator()).isNotNull();
    assertThat(configuration.maxRetryAttempts()).isEqualTo(3);
  }

  @Test
  void builderRejectsAMissingRequiredPolicy() {
    RefundConfiguration.Builder builder =
        RefundConfiguration.builder()
            .lifecycleProvider(RefundConfigurations.standard().lifecycleProvider());

    org.assertj.core.api.Assertions.assertThatThrownBy(builder::build)
        .isInstanceOf(io.genfin.api.exception.GenFinException.class);
  }

  @Test
  void builderRejectsANegativeMaxRetryAttempts() {
    RefundConfiguration standard = RefundConfigurations.standard();
    RefundConfiguration.Builder builder =
        RefundConfiguration.builder()
            .lifecycleProvider(standard.lifecycleProvider())
            .approvalPolicy(standard.approvalPolicy())
            .approvalWorkflow(standard.approvalWorkflow())
            .windowPolicy(standard.windowPolicy())
            .maximumRefundPolicy(standard.maximumRefundPolicy())
            .reasonPolicy(standard.reasonPolicy())
            .calculator(standard.calculator())
            .validator(standard.validator())
            .requestValidator(standard.requestValidator())
            .maxRetryAttempts(-1);

    org.assertj.core.api.Assertions.assertThatThrownBy(builder::build)
        .isInstanceOf(io.genfin.api.exception.GenFinException.class);
  }
}
