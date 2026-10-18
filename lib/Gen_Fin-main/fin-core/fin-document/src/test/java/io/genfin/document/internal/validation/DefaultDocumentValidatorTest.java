package io.genfin.document.internal.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.letterhead.StandardLetterheadFormat;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.result.PdfRenderInputs;
import io.genfin.document.port.DocumentRenderers;
import io.genfin.document.port.RendererConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DefaultDocumentValidatorTest {

  private static ComposedDocument emptyDocument() {
    return ComposedDocument.of(
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
        List.of(),
        DocumentAttributes.empty());
  }

  @Test
  void validRequestProducesNoIssues() {
    DefaultDocumentValidator validator = new DefaultDocumentValidator(DocumentRenderers.standard());

    var result =
        validator.validate(
            emptyDocument(), RendererConfiguration.defaults(), RendererId.of("json"));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void unregisteredRendererProducesAnIssue() {
    DefaultDocumentValidator validator = new DefaultDocumentValidator(DocumentRenderers.standard());

    var result =
        validator.validate(
            emptyDocument(), RendererConfiguration.defaults(), RendererId.of("nonexistent"));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues().stream().map(i -> i.code()).collect(Collectors.toList()))
        .contains("RENDERER_NOT_FOUND");
  }

  @Test
  void unsupportedLetterheadFormatProducesAnIssue() {
    DefaultDocumentValidator validator = new DefaultDocumentValidator(DocumentRenderers.standard());
    Letterhead svgLetterhead =
        Letterhead.of(
            LetterheadId.of("lh-1"), StandardLetterheadFormat.SVG, new byte[] {1}, "image/svg+xml");
    PdfRenderInputs inputs =
        PdfRenderInputs.builder(
                PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build())
            .letterhead(svgLetterhead)
            .build();

    var result =
        validator.validate(
            emptyDocument(),
            RendererConfiguration.defaults().withPdfInputs(inputs),
            RendererId.of("pdf"));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues().stream().map(i -> i.code()).collect(Collectors.toList()))
        .contains("UNSUPPORTED_LETTERHEAD_FORMAT");
  }
}
