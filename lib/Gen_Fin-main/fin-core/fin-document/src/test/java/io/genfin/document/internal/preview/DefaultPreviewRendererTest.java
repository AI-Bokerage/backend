package io.genfin.document.internal.preview;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.preview.DocumentPreview;
import io.genfin.document.api.preview.PreviewConfiguration;
import io.genfin.document.port.DocumentRenderers;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultPreviewRendererTest {

  @Test
  void previewDelegatesToResolvedRendererAndProducesEquivalentOutput() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
            List.of(DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    var resolver = DocumentRenderers.standard();
    DefaultPreviewRenderer previewRenderer = new DefaultPreviewRenderer(resolver);

    DocumentPreview preview =
        previewRenderer.preview(
            document,
            RendererConfiguration.defaults(),
            PreviewConfiguration.builder().targetRenderer(RendererId.of("json")).build());

    String directOutput =
        new String(
            resolver
                .resolve(RendererId.of("json"))
                .render(document, RendererConfiguration.defaults())
                .content(),
            StandardCharsets.UTF_8);
    String previewOutput =
        new String(preview.result().renderResult().content(), StandardCharsets.UTF_8);

    assertThat(previewOutput).isEqualTo(directOutput);
  }
}
