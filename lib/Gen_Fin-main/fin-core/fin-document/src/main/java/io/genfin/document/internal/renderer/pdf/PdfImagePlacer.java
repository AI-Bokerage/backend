package io.genfin.document.internal.renderer.pdf;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

final class PdfImagePlacer {

  private static final float LOGO_SIZE = 60f;
  private static final float QR_SIZE = 80f;

  private final PdfDocumentBuilder documentBuilder;

  PdfImagePlacer(PdfDocumentBuilder documentBuilder) {
    this.documentBuilder = documentBuilder;
  }

  void drawTopLeft(PDPage page, byte[] imageBytes) throws IOException {
    PDImageXObject image =
        PDImageXObject.createFromByteArray(documentBuilder.document(), imageBytes, "logo");
    float y = documentBuilder.contentTop() - LOGO_SIZE;
    drawImage(page, image, documentBuilder.contentLeft(), y, LOGO_SIZE, LOGO_SIZE);
  }

  void drawBottomRight(PDPage page, byte[] imageBytes) throws IOException {
    PDImageXObject image =
        PDImageXObject.createFromByteArray(documentBuilder.document(), imageBytes, "qr");
    float x = documentBuilder.contentLeft() + documentBuilder.contentWidth() - QR_SIZE;
    float y = documentBuilder.contentBottom();
    drawImage(page, image, x, y, QR_SIZE, QR_SIZE);
  }

  private void drawImage(
      PDPage page, PDImageXObject image, float x, float y, float width, float height)
      throws IOException {
    try (PDPageContentStream contentStream =
        new PDPageContentStream(
            documentBuilder.document(), page, PDPageContentStream.AppendMode.APPEND, true)) {
      contentStream.drawImage(image, x, y, width, height);
    }
  }
}
