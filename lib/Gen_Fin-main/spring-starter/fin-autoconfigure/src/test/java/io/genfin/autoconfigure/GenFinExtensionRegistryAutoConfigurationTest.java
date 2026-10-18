package io.genfin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class GenFinExtensionRegistryAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GenFinExtensionRegistryAutoConfiguration.class));

  @Test
  void providesADefaultExtensionRegistry() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(ExtensionRegistry.class));
  }

  @Test
  void userSuppliedRegistrySuppressesTheDefault() {
    contextRunner
        .withUserConfiguration(CustomRegistryConfig.class)
        .run(
            context ->
                assertThat(context.getBean(ExtensionRegistry.class))
                    .isSameAs(CustomRegistryConfig.CUSTOM));
  }

  @Configuration
  static class CustomRegistryConfig {
    static final ExtensionRegistry CUSTOM = ExtensionRegistries.create();

    @Bean
    ExtensionRegistry extensionRegistry() {
      return CUSTOM;
    }
  }
}
