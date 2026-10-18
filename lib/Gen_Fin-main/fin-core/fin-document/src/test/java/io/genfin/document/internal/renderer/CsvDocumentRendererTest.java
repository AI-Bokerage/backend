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

class CsvDocumentRendererTest {

  @Test
  void rendersTableElementAsCsvRowsAndSkipsNonTabularElements() throws Exception {
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
                    "Lines",
                    List.of(
                        TableElement.of(List.of("Item", "Qty"), List.of(List.of("Widget", "2"))),
                        KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    CsvDocumentRenderer renderer = new CsvDocumentRenderer();
    String csv =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(csv).contains("Item,Qty");
    assertThat(csv).contains("Widget,2");
    assertThat(csv).doesNotContain("Total");
  }

  @Test
  void idAndCapabilitiesAreCsv() {
    CsvDocumentRenderer renderer = new CsvDocumentRenderer();
    assertThat(renderer.id().toString()).contains("csv");
    assertThat(renderer.capabilities().mimeType()).isEqualTo("text/csv");
  }

  @Test
  void escapesSpecialCharactersInCsvFields() throws Exception {
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
                    "Data",
                    List.of(
                        TableElement.of(
                            List.of("Header,with,commas", "Value\"with\"quotes"),
                            List.of(
                                List.of("Cell,value,here", "Quote\"inside"),
                                List.of("Multi\nline\nvalue", "Combined,\"test\"")))))),
            DocumentAttributes.empty());

    CsvDocumentRenderer renderer = new CsvDocumentRenderer();
    String csv =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    // Headers should be escaped
    assertThat(csv).contains("\"Header,with,commas\"");
    assertThat(csv).contains("\"Value\"\"with\"\"quotes\"");

    // Data rows should be escaped
    assertThat(csv).contains("\"Cell,value,here\"");
    assertThat(csv).contains("\"Quote\"\"inside\"");
    assertThat(csv).contains("\"Multi\nline\nvalue\"");
    assertThat(csv).contains("\"Combined,\"\"test\"\"\"");
  }
}
