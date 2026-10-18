package io.genfin.document.internal.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlainTextDocumentRendererTest {

  @Test
  void rendersSectionTitleAndKeyValuePairs() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    PlainTextDocumentRenderer renderer = new PlainTextDocumentRenderer();
    String text =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(text).contains("Summary");
    assertThat(text).contains("Total: 500.00");
  }

  @Test
  void rendersTableWithHeadersAndRows() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(
                DocumentSection.of(
                    "Items",
                    List.of(
                        TableElement.of(
                            List.of("Name", "Qty"),
                            List.of(List.of("Item1", "5"), List.of("Item2", "3")))))),
            DocumentAttributes.empty());

    PlainTextDocumentRenderer renderer = new PlainTextDocumentRenderer();
    String text =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(text).contains("Name | Qty");
    assertThat(text).contains("Item1 | 5");
    assertThat(text).contains("Item2 | 3");
  }
}
