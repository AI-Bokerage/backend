package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.result.PdfRenderInputs;
import org.junit.jupiter.api.Test;

class RendererConfigurationPdfInputsTest {

  @Test
  void defaultsHasNoPdfInputs() {
    assertThat(RendererConfiguration.defaults().pdfInputs()).isEmpty();
  }

  @Test
  void withPdfInputsCarriesInputsAndPreservesExistingOptions() {
    DocumentAttributes options = DocumentAttributes.of(java.util.Map.of("k", "v"));
    PdfRenderInputs inputs =
        PdfRenderInputs.builder(
                PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build())
            .build();

    RendererConfiguration configuration = RendererConfiguration.of(options).withPdfInputs(inputs);

    assertThat(configuration.pdfInputs()).contains(inputs);
    assertThat(configuration.options().get("k")).contains("v");
  }
}
