package io.genfin.document.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class IdentityTypesTest {

  @Test
  void allIdentityTypesSupportOfAndGenerate() {
    List<Function<String, ? extends Object>> ofFactories =
        List.of(
            TemplateId::of,
            RendererId::of,
            BrandId::of,
            ThemeId::of,
            LayoutId::of,
            LetterheadId::of,
            AttachmentId::of,
            WatermarkId::of);

    for (Function<String, ? extends Object> factory : ofFactories) {
      assertThat(factory.apply("x-1").toString()).contains("x-1");
    }

    assertThat(TemplateId.generate().value()).isNotBlank();
    assertThat(RendererId.generate().value()).isNotBlank();
    assertThat(BrandId.generate().value()).isNotBlank();
    assertThat(ThemeId.generate().value()).isNotBlank();
    assertThat(LayoutId.generate().value()).isNotBlank();
    assertThat(LetterheadId.generate().value()).isNotBlank();
    assertThat(AttachmentId.generate().value()).isNotBlank();
    assertThat(WatermarkId.generate().value()).isNotBlank();
  }
}
