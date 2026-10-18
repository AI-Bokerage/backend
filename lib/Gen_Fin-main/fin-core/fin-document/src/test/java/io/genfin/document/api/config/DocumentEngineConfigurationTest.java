package io.genfin.document.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.BrandId;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import org.junit.jupiter.api.Test;

class DocumentEngineConfigurationTest {

  @Test
  void zeroConfigDefaultsAreSensible() {
    DocumentEngineConfiguration config = DocumentEngineConfiguration.builder().build();

    assertThat(config.defaultRenderer()).isEqualTo(RendererId.of("json"));
    assertThat(config.defaultPaperSize()).isEqualTo(StandardPaperSize.A4);
    assertThat(config.defaultOrientation()).isEqualTo(StandardOrientation.PORTRAIT);
    assertThat(config.defaultBrand()).isEmpty();
    assertThat(config.defaultLetterhead()).isEmpty();
    assertThat(config.defaultMargins().topPoints()).isZero();
    assertThat(config.defaultPageNumbers().enabled()).isFalse();
    assertThat(config.previewEnabled()).isFalse();
    assertThat(config.watermarkEnabled()).isFalse();
  }

  @Test
  void explicitValuesAreRespected() {
    DocumentEngineConfiguration config =
        DocumentEngineConfiguration.builder()
            .defaultRenderer(RendererId.of("pdf"))
            .defaultBrand(BrandId.of("brand-1"))
            .previewEnabled(true)
            .build();

    assertThat(config.defaultRenderer()).isEqualTo(RendererId.of("pdf"));
    assertThat(config.defaultBrand()).contains(BrandId.of("brand-1"));
    assertThat(config.previewEnabled()).isTrue();
  }

  @Test
  void explicitNullOptionalSettersCoalesceToDefaultsRatherThanNull() {
    DocumentEngineConfiguration config =
        DocumentEngineConfiguration.builder()
            .defaultRenderer(null)
            .defaultPaperSize(null)
            .defaultOrientation(null)
            .defaultMargins(null)
            .defaultPageNumbers(null)
            .build();

    assertThat(config.defaultRenderer()).isEqualTo(RendererId.of("json"));
    assertThat(config.defaultPaperSize()).isEqualTo(StandardPaperSize.A4);
    assertThat(config.defaultOrientation()).isEqualTo(StandardOrientation.PORTRAIT);
    assertThat(config.defaultMargins()).isNotNull();
    assertThat(config.defaultPageNumbers()).isNotNull();
  }
}
