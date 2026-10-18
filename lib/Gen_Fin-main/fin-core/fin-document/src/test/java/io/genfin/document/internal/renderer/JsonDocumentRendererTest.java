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

class JsonDocumentRendererTest {

  @Test
  void rendersKeyValueSectionAsJson() throws Exception {
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

    JsonDocumentRenderer renderer = new JsonDocumentRenderer();
    String json =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(json).contains("\"documentId\":\"doc-1\"");
    assertThat(json).contains("{\"label\":\"Total\",\"value\":\"500.00\"}");
  }

  @Test
  void idAndCapabilitiesAreJson() {
    JsonDocumentRenderer renderer = new JsonDocumentRenderer();
    assertThat(renderer.id().toString()).contains("json");
    assertThat(renderer.capabilities().mimeType()).isEqualTo("application/json");
  }

  @Test
  void escapesQuotesAndBackslashes() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-with\"quote"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(
                DocumentSection.of(
                    "Section\\with\\backslash",
                    List.of(KeyValueElement.of("Key\"with\"quotes", "Value\nwith\nnewlines")))),
            DocumentAttributes.empty());

    JsonDocumentRenderer renderer = new JsonDocumentRenderer();
    String json =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    // Verify escaped sequences are present in the output
    assertThat(json).contains("\\\"");
    assertThat(json).contains("\\n");
    assertThat(json).contains("\\\\");
    // Verify the JSON structure is still valid by checking key patterns
    assertThat(json).contains("\"documentId\"");
    assertThat(json).contains("\"sections\"");
  }

  @Test
  void rendersTableElementWithRealRowData() throws Exception {
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

    JsonDocumentRenderer renderer = new JsonDocumentRenderer();
    String json =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(json)
        .contains(
            "\"table\":{\"headers\":[\"Name\",\"Qty\"],"
                + "\"rows\":[[\"Item1\",\"5\"],[\"Item2\",\"3\"]]}");
    assertThat(json).doesNotContain("\"2x2\"");
  }

  @Test
  void elementsArePreservedAsOrderedArrayEvenWithDuplicateLabels() throws Exception {
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
                    "Summary",
                    List.of(
                        KeyValueElement.of("Total", "100.00"),
                        KeyValueElement.of("Total", "200.00")))),
            DocumentAttributes.empty());

    JsonDocumentRenderer renderer = new JsonDocumentRenderer();
    String json =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(json).contains("\"elements\":[");
    assertThat(json).contains("{\"label\":\"Total\",\"value\":\"100.00\"}");
    assertThat(json).contains("{\"label\":\"Total\",\"value\":\"200.00\"}");
  }

  @Test
  void rendersLocaleCurrencyCreatedAtAndAttributes() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.of(java.util.Map.of("region", "IN")));

    JsonDocumentRenderer renderer = new JsonDocumentRenderer();
    String json =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(json).contains("\"locale\":\"en-IN\"");
    assertThat(json).contains("\"currency\":\"INR\"");
    assertThat(json).contains("\"createdAt\":\"" + Instant.EPOCH + "\"");
    assertThat(json).contains("\"attributes\":{\"region\":\"IN\"}");
  }
}
