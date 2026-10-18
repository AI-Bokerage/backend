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

class XmlDocumentRendererTest {

  @Test
  void rendersSectionAndElementAsXml() throws Exception {
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

    XmlDocumentRenderer renderer = new XmlDocumentRenderer();
    String xml =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    assertThat(xml).contains("<document id=\"doc-1\">");
    assertThat(xml).contains("<field label=\"Total\">500.00</field>");
  }

  @Test
  void idAndCapabilitiesAreXml() {
    XmlDocumentRenderer renderer = new XmlDocumentRenderer();
    assertThat(renderer.id().toString()).contains("xml");
    assertThat(renderer.capabilities().mimeType()).isEqualTo("application/xml");
  }

  @Test
  void escapesSpecialCharactersInXmlContent() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-with&id"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            List.of(
                DocumentSection.of(
                    "Section<with>angles",
                    List.of(
                        KeyValueElement.of(
                            "Label&with&ampersands", "Value<with>brackets&quotes\"here"),
                        KeyValueElement.of("Another\"quoted\"label", "Text&with&special")))),
            DocumentAttributes.empty());

    XmlDocumentRenderer renderer = new XmlDocumentRenderer();
    String xml =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    // Document ID should be escaped in attribute
    assertThat(xml).contains("<document id=\"doc-with&amp;id\">");

    // Section title should be escaped in attribute
    assertThat(xml).contains("<section title=\"Section&lt;with&gt;angles\">");

    // Field values should have escaped content (not just in attributes)
    assertThat(xml)
        .contains(
            "<field label=\"Label&amp;with&amp;ampersands\">Value&lt;with&gt;brackets&amp;quotes&quot;here</field>");
    assertThat(xml)
        .contains(
            "<field label=\"Another&quot;quoted&quot;label\">Text&amp;with&amp;special</field>");
  }

  @Test
  void rendersTableWithEscapedContent() throws Exception {
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
                            List.of("Header&one", "Header\"two"),
                            List.of(
                                List.of("Cell<with>brackets", "Cell&with&ampersand"),
                                List.of("Cell\"with\"quotes", "Normal")))))),
            DocumentAttributes.empty());

    XmlDocumentRenderer renderer = new XmlDocumentRenderer();
    String xml =
        new String(
            renderer.render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);

    // Verify table structure with escaped headers
    assertThat(xml).contains("<headers>");
    assertThat(xml).contains("<header>Header&amp;one</header>");
    assertThat(xml).contains("<header>Header&quot;two</header>");
    assertThat(xml).contains("</headers>");

    // Verify table rows with escaped cells
    assertThat(xml).contains("<row>");
    assertThat(xml).contains("<cell>Cell&lt;with&gt;brackets</cell>");
    assertThat(xml).contains("<cell>Cell&amp;with&amp;ampersand</cell>");
    assertThat(xml).contains("<cell>Cell&quot;with&quot;quotes</cell>");
    assertThat(xml).contains("<cell>Normal</cell>");
    assertThat(xml).contains("</row>");
  }
}
