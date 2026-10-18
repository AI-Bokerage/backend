package io.genfin.document.internal.renderer.pdf;

import io.genfin.document.api.layout.LayoutFooter;
import io.genfin.document.api.layout.LayoutHeader;
import io.genfin.document.api.layout.PageNumberConfiguration;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

final class PdfHeaderFooterPainter {

  private static final float FONT_SIZE = 9f;
  private static final float MARGIN_GAP = 20f;
  private static final float EDGE_PADDING = 2f;
  private static final String PAGE_NUMBER_TOKEN = "{n}";
  private static final String TOTAL_PAGES_TOKEN = "{total}";

  private final PdfDocumentBuilder documentBuilder;
  private final PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

  PdfHeaderFooterPainter(PdfDocumentBuilder documentBuilder) {
    this.documentBuilder = documentBuilder;
  }

  void paint(
      PDPage page,
      LayoutHeader header,
      LayoutFooter footer,
      PageNumberConfiguration pageNumbers,
      int pageNumber,
      int totalPages)
      throws IOException {
    String pageNumberText =
        pageNumbers.enabled() ? resolve(pageNumbers, pageNumber, totalPages) : "";
    if (header.text().isBlank() && footer.text().isBlank() && pageNumberText.isBlank()) {
      return;
    }

    PDRectangle mediaBox = page.getMediaBox();
    // Header/footer live in the page margins, but must stay inside the MediaBox to be visible at
    // all: with Margins.none() the raw margin position would fall off the page entirely.
    float headerY =
        Math.min(documentBuilder.contentTop() + MARGIN_GAP, mediaBox.getUpperRightY() - FONT_SIZE);
    float footerY =
        Math.max(
            documentBuilder.contentBottom() - MARGIN_GAP, mediaBox.getLowerLeftY() + EDGE_PADDING);
    float left = documentBuilder.contentLeft();
    float right = left + documentBuilder.contentWidth();

    try (PDPageContentStream contentStream =
        new PDPageContentStream(
            documentBuilder.document(), page, PDPageContentStream.AppendMode.APPEND, true)) {
      if (!header.text().isBlank()) {
        drawText(contentStream, header.text(), left, headerY);
      }
      if (!footer.text().isBlank()) {
        drawText(contentStream, footer.text(), left, footerY);
      }
      if (!pageNumberText.isBlank()) {
        drawText(contentStream, pageNumberText, right - textWidth(pageNumberText), footerY);
      }
    }
  }

  private static String resolve(
      PageNumberConfiguration pageNumbers, int pageNumber, int totalPages) {
    return pageNumbers
        .format()
        .replace(PAGE_NUMBER_TOKEN, String.valueOf(pageNumber))
        .replace(TOTAL_PAGES_TOKEN, String.valueOf(totalPages));
  }

  private float textWidth(String text) throws IOException {
    return font.getStringWidth(text) / 1000f * FONT_SIZE;
  }

  private void drawText(PDPageContentStream contentStream, String text, float x, float y)
      throws IOException {
    contentStream.beginText();
    contentStream.setFont(font, FONT_SIZE);
    contentStream.newLineAtOffset(x, y);
    contentStream.showText(text);
    contentStream.endText();
  }
}
