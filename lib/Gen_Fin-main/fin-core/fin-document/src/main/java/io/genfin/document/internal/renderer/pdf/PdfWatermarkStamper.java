package io.genfin.document.internal.renderer.pdf;

import io.genfin.document.api.watermark.Watermark;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

/** Draws a {@link Watermark}'s label as large diagonal semi-transparent text. */
final class PdfWatermarkStamper {

  private static final float FONT_SIZE = 60f;

  private final PdfDocumentBuilder documentBuilder;
  private final PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

  PdfWatermarkStamper(PdfDocumentBuilder documentBuilder) {
    this.documentBuilder = documentBuilder;
  }

  // Only StandardWatermarkPlacement.DIAGONAL_CENTER is implemented in this MVP;
  // any other WatermarkPlacement value still stamps at this same position.
  void stamp(PDPage page, Watermark watermark) throws IOException {
    float centerX = documentBuilder.contentLeft() + documentBuilder.contentWidth() / 2f;
    float centerY = (documentBuilder.contentTop() + documentBuilder.contentBottom()) / 2f;

    try (PDPageContentStream contentStream =
        new PDPageContentStream(
            documentBuilder.document(), page, PDPageContentStream.AppendMode.APPEND, true)) {
      PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
      graphicsState.setNonStrokingAlphaConstant((float) watermark.opacity().value());
      contentStream.setGraphicsStateParameters(graphicsState);

      contentStream.beginText();
      contentStream.setFont(font, FONT_SIZE);
      contentStream.setTextMatrix(
          Matrix.getRotateInstance(Math.toRadians(45), centerX - 150f, centerY));
      contentStream.showText(watermark.label().code());
      contentStream.endText();
    }
  }
}
