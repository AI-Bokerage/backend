package io.genfin.document.api.preview;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.RendererId;
import org.junit.jupiter.api.Test;

class PreviewConfigurationTest {

  @Test
  void defaultsToPdfRendererWhenUnset() {
    PreviewConfiguration config = PreviewConfiguration.builder().build();
    assertThat(config.targetRenderer()).isEqualTo(RendererId.of("pdf"));
  }

  @Test
  void explicitNullTargetRendererCoalescesToDefault() {
    PreviewConfiguration config = PreviewConfiguration.builder().targetRenderer(null).build();
    assertThat(config.targetRenderer()).isEqualTo(RendererId.of("pdf"));
  }

  @Test
  void exposesGivenTargetRenderer() {
    PreviewConfiguration config =
        PreviewConfiguration.builder().targetRenderer(RendererId.of("json")).build();
    assertThat(config.targetRenderer()).isEqualTo(RendererId.of("json"));
  }
}
