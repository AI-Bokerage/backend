package io.genfin.autoconfigure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class DocumentAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GenFinExtensionRegistryAutoConfiguration.class, DocumentAutoConfiguration.class));

  @Test
  void defaultRendererPropertyMatchesABuiltInRenderer() {
    contextRunner.run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void unknownDefaultRendererFailsFast() {
    contextRunner
        .withPropertyValues("genfin.document.renderer=does-not-exist")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void customRendererBeanIsDiscoverable() {
    contextRunner
        .withUserConfiguration(CustomRendererConfig.class)
        .withPropertyValues("genfin.document.renderer=custom")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Configuration
  static class CustomRendererConfig {
    @Bean
    DocumentRenderer customRenderer() {
      DocumentRenderer renderer = mock(DocumentRenderer.class);
      when(renderer.id()).thenReturn(RendererId.of("custom"));
      when(renderer.capabilities()).thenReturn(mock(RendererCapabilities.class));
      return renderer;
    }
  }
}
