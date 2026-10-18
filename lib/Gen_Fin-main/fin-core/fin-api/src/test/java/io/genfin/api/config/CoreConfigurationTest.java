package io.genfin.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ConfigurationException;
import org.junit.jupiter.api.Test;

class CoreConfigurationTest {

  @Test
  void getReturnsTypedValue() {
    CoreConfiguration config = CoreConfiguration.builder().set("retries", 3).build();

    assertThat(config.get("retries", Integer.class)).isEqualTo(3);
  }

  @Test
  void missingKeyThrowsConfigurationException() {
    CoreConfiguration config = CoreConfiguration.empty();

    assertThatThrownBy(() -> config.get("missing", String.class))
        .isInstanceOf(ConfigurationException.class);
  }

  @Test
  void wrongTypeThrowsConfigurationException() {
    CoreConfiguration config = CoreConfiguration.builder().set("retries", "not-a-number").build();

    assertThatThrownBy(() -> config.get("retries", Integer.class))
        .isInstanceOf(ConfigurationException.class);
  }

  @Test
  void findReturnsEmptyForMissingKey() {
    CoreConfiguration config = CoreConfiguration.empty();

    assertThat(config.find("missing", String.class)).isEmpty();
    assertThat(config.contains("missing")).isFalse();
  }
}
