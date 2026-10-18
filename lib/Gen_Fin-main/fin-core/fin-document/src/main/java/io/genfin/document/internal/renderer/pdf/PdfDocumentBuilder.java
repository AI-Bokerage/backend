package io.genfin.document.internal.renderer.pdf;

import io.genfin.document.api.layout.Margins;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

final class PdfDocumentBuilder {

  private static final Map<String, PDRectangle> PAPER_SIZES =
      Map.of(
          StandardPaperSize.A4.code(), PDRectangle.A4,
          StandardPaperSize.LETTER.code(), PDRectangle.LETTER);

  private final PDDocument document = new PDDocument();
  private final PDRectangle pageSize;
  private final Margins margins;

  PdfDocumentBuilder(PageLayout layout) {
    PDRectangle basePaperSize = PAPER_SIZES.getOrDefault(layout.paperSize().code(), PDRectangle.A4);
    boolean landscape = StandardOrientation.LANDSCAPE.code().equals(layout.orientation().code());
    this.pageSize =
        landscape
            ? new PDRectangle(basePaperSize.getHeight(), basePaperSize.getWidth())
            : basePaperSize;
    this.margins = layout.margins();
  }

  PDPage addPage() {
    PDPage page = new PDPage(pageSize);
    document.addPage(page);
    return page;
  }

  PDDocument document() {
    return document;
  }

  float contentLeft() {
    return margins.leftPoints();
  }

  float contentTop() {
    return pageSize.getHeight() - margins.topPoints();
  }

  float contentBottom() {
    return margins.bottomPoints();
  }

  float contentWidth() {
    return pageSize.getWidth() - margins.leftPoints() - margins.rightPoints();
  }
}
