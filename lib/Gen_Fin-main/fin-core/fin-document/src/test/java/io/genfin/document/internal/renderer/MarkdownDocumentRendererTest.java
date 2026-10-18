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

class MarkdownDocumentRendererTest {

  @Test
  void rendersSectionAsMarkdownHeadingAndList() throws Exception {
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

    MarkdownDocumentRenderer renderer = new MarkdownDocumentRenderer();
    String markdown =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(markdown).contains("## Summary");
    assertThat(markdown).contains("- **Total**: 500.00");
  }

  @Test
  void rendersTableAsValidMarkdownWithSeparatorRow() throws Exception {
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

    MarkdownDocumentRenderer renderer = new MarkdownDocumentRenderer();
    String markdown =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(markdown).contains("| Name | Qty |");
    assertThat(markdown).contains("| --- | --- |");
    assertThat(markdown).contains("| Item1 | 5 |");
    assertThat(markdown).contains("| Item2 | 3 |");
  }

  @Test
  void escapesPipeCharacterInHeadersAndCells() throws Exception {
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
                            List.of("Name|Alt", "Qty"), List.of(List.of("Item|1", "5")))))),
            DocumentAttributes.empty());

    MarkdownDocumentRenderer renderer = new MarkdownDocumentRenderer();
    String markdown =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(markdown).contains("| Name\\|Alt | Qty |");
    assertThat(markdown).contains("| Item\\|1 | 5 |");
    // the header row must still have exactly 2 data columns (3 pipe-delimited segments)
    String headerLine =
        markdown.lines().filter(line -> line.startsWith("| Name")).findFirst().orElseThrow();
    assertThat(headerLine.split("(?<!\\\\)\\|")).hasSize(3);
  }

  @Test
  void escapesBoldMarkerInKeyValueLabel() throws Exception {
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
                    "Summary", List.of(KeyValueElement.of("Bold**Label", "500.00")))),
            DocumentAttributes.empty());

    MarkdownDocumentRenderer renderer = new MarkdownDocumentRenderer();
    String markdown =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(markdown).contains("- **Bold\\*\\*Label**: 500.00");
  }
}
