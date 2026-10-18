package io.genfin.document.internal.renderer.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentElement;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

class PdfContentPainterTest {

  private static PageLayout layout() {
    return PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
  }

  private static ComposedDocument documentOf(String sectionTitle, List<DocumentElement> elements) {
    return ComposedDocument.of(
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
        List.of(DocumentSection.of(sectionTitle, elements)),
        DocumentAttributes.empty());
  }

  @Test
  void paintsTextBlockAndKeyValueContent() throws Exception {
    ComposedDocument document =
        documentOf(
            "Summary",
            List.of(
                KeyValueElement.of("Total", "500.00"),
                TextBlockElement.of("Thank you for your business.")));

    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout());
    PdfContentPainter painter = new PdfContentPainter(documentBuilder);
    painter.paint(document);

    String text = new PDFTextStripper().getText(documentBuilder.document());

    assertThat(text).contains("Summary");
    assertThat(text).contains("Total");
    assertThat(text).contains("500.00");
    assertThat(text).contains("Thank you for your business.");
  }

  @Test
  void paintsTableHeadersAndRows() throws Exception {
    ComposedDocument document =
        documentOf(
            "Line Items",
            List.of(TableElement.of(List.of("Item", "Qty"), List.of(List.of("Widget", "2")))));

    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout());
    PdfContentPainter painter = new PdfContentPainter(documentBuilder);
    painter.paint(document);

    String text = new PDFTextStripper().getText(documentBuilder.document());

    assertThat(text).contains("Item");
    assertThat(text).contains("Qty");
    assertThat(text).contains("Widget");
  }

  @Test
  void overflowingContentStartsANewPage() throws Exception {
    List<DocumentElement> manyLines = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      manyLines.add(TextBlockElement.of("Line number " + i));
    }

    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout());
    PdfContentPainter painter = new PdfContentPainter(documentBuilder);
    painter.paint(documentOf("Body", manyLines));

    assertThat(documentBuilder.document().getNumberOfPages()).isGreaterThan(1);
  }

  @Test
  void longTextWrapsOntoMultipleLinesThatFitTheContentWidth() throws Exception {
    String paragraph = ("The quick brown fox jumps over the lazy dog. ").repeat(12).trim();

    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout());
    PdfContentPainter painter = new PdfContentPainter(documentBuilder);
    painter.paint(documentOf("Notes", List.of(TextBlockElement.of(paragraph))));

    String text = new PDFTextStripper().getText(documentBuilder.document());
    List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();

    assertThat(lines).hasSizeGreaterThan(2);
    var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    for (String line : lines.subList(1, lines.size())) {
      assertThat(font.getStringWidth(line) / 1000f * 11f)
          .as("line must fit content width: %s", line)
          .isLessThanOrEqualTo(documentBuilder.contentWidth());
    }
    assertThat(String.join(" ", lines.subList(1, lines.size()))).isEqualTo(paragraph);
  }

  @Test
  void firstLineOfEachPageIsWithinTheVisiblePageBounds() throws Exception {
    List<DocumentElement> manyLines = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      manyLines.add(TextBlockElement.of("Line number " + i));
    }

    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout());
    PdfContentPainter painter = new PdfContentPainter(documentBuilder);
    painter.paint(documentOf("Body", manyLines));

    List<TextPosition> positions = new ArrayList<>();
    PDFTextStripper stripper =
        new PDFTextStripper() {
          @Override
          protected void writeString(String text, List<TextPosition> textPositions) {
            positions.addAll(textPositions);
          }
        };
    stripper.getText(documentBuilder.document());

    assertThat(documentBuilder.document().getNumberOfPages()).isGreaterThan(1);
    assertThat(positions).isNotEmpty();
    float pageHeight = documentBuilder.document().getPage(0).getMediaBox().getHeight();
    for (TextPosition position : positions) {
      // getY() is the baseline measured downward from the page top; subtracting the glyph height
      // gives the glyph top, which must still be on the page.
      assertThat(position.getY() - position.getHeight())
          .as("glyph top must be on the page: %s", position.getUnicode())
          .isGreaterThanOrEqualTo(0f);
      assertThat(position.getY()).isLessThanOrEqualTo(pageHeight);
    }
  }

  @Test
  void nonWinAnsiCharactersDegradeInsteadOfFailingTheRender() throws Exception {
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout());
    PdfContentPainter painter = new PdfContentPainter(documentBuilder);

    painter.paint(
        documentOf(
            "Notes",
            List.of(TextBlockElement.of("Total 日本 ₹500 ok"), KeyValueElement.of("Grüße", "₺42"))));

    String text = new PDFTextStripper().getText(documentBuilder.document());
    assertThat(text).contains("Total");
    assertThat(text).contains("ok");
    assertThat(text).contains("500");
  }

  @Test
  void multiLineTextBlocksKeepRealSpacesInsteadOfPlaceholders() throws Exception {
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout());
    PdfContentPainter painter = new PdfContentPainter(documentBuilder);

    painter.paint(
        documentOf("Notes", List.of(TextBlockElement.of("Line one\nLine two\tTabbed\r\nEnd"))));

    String text = new PDFTextStripper().getText(documentBuilder.document());

    assertThat(text).doesNotContain("?");
    assertThat(text).contains("Line one Line two Tabbed End");
  }

  @Test
  void aSingleWordWiderThanTheContentWidthIsHardBroken() throws Exception {
    String giantWord = "X".repeat(400);

    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout());
    PdfContentPainter painter = new PdfContentPainter(documentBuilder);
    painter.paint(documentOf("Notes", List.of(TextBlockElement.of(giantWord))));

    String text = new PDFTextStripper().getText(documentBuilder.document());
    var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    for (String line : text.lines().filter(line -> !line.isBlank()).skip(1).toList()) {
      assertThat(font.getStringWidth(line) / 1000f * 11f)
          .isLessThanOrEqualTo(documentBuilder.contentWidth());
    }
    assertThat(text.replace("\n", "").replace("\r", "")).contains(giantWord);
  }
}
