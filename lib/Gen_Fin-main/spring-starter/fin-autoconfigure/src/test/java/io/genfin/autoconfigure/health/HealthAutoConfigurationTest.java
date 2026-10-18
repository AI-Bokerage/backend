package io.genfin.autoconfigure.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HealthAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(HealthAutoConfiguration.class));

  @Test
  void reportsUpWithNoExternalCalls() {
    contextRunner.run(
        context -> {
          HealthIndicator indicator = context.getBean(HealthIndicator.class);
          Health health = indicator.health();
          assertThat(health.getStatus()).isEqualTo(Status.UP);
          assertThat(health.getDetails()).containsKey("customDocumentRenderersLoaded");
        });
  }
}
