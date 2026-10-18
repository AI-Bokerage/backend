package io.genfin.document.internal.renderer.pdf;

import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.letterhead.StandardLetterheadFormat;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

final class PdfLetterheadOverlay {

  private static final String IMAGE_NAME = "letterhead";

  // No PdfDocumentBuilder is taken (unlike the other painters): the overlay works purely on the
  // document and pages it is handed, and an unused field/parameter fails the static analysis gate.

  void overlay(PDDocument targetDocument, Letterhead letterhead) throws IOException {
    String format = letterhead.format().code();

    if (StandardLetterheadFormat.BLANK.code().equals(format)) {
      return;
    }
    if (StandardLetterheadFormat.PDF.code().equals(format)) {
      overlayPdfLetterhead(targetDocument, letterhead);
      return;
    }
    if (StandardLetterheadFormat.PNG.code().equals(format)
        || StandardLetterheadFormat.JPEG.code().equals(format)) {
      overlayImageLetterhead(targetDocument, letterhead);
      return;
    }

    throw new UnsupportedOperationException(
        "Letterhead format '"
            + format
            + "' is not supported by this PDF renderer (see Stage 8 Future Roadmap)");
  }

  private void overlayPdfLetterhead(PDDocument targetDocument, Letterhead letterhead)
      throws IOException {
    try (PDDocument letterheadDocument = Loader.loadPDF(letterhead.content())) {
      LayerUtility layerUtility = new LayerUtility(targetDocument);
      PDPage letterheadPage = letterheadDocument.getPage(0);
      // importPageAsForm shifts the letterhead page's visible area (its CropBox, falling back to
      // the MediaBox) towards 0,0, so the imported form's space is origin-normalised. The form is
      // imported once and re-drawn on every page.
      PDFormXObject letterheadForm =
          layerUtility.importPageAsForm(letterheadDocument, letterheadPage);
      PDRectangle sourceBox = letterheadPage.getCropBox();
      // LayerUtility applies that shift twice when the CropBox origin differs from the MediaBox
      // origin (PDFBox 3.0.7 translates by both mediaLL - cropLL and -cropLL), which lands the
      // letterhead off by that difference. Undo the surplus shift here.
      PDRectangle mediaBox = letterheadPage.getMediaBox();
      float surplusShiftX = sourceBox.getLowerLeftX() - mediaBox.getLowerLeftX();
      float surplusShiftY = sourceBox.getLowerLeftY() - mediaBox.getLowerLeftY();
      for (PDPage targetPage : targetDocument.getPages()) {
        drawBackground(
            targetDocument, targetPage, letterheadForm, sourceBox, surplusShiftX, surplusShiftY);
      }
    }
  }

  private void drawBackground(
      PDDocument targetDocument,
      PDPage targetPage,
      PDFormXObject form,
      PDRectangle sourceBox,
      float surplusShiftX,
      float surplusShiftY)
      throws IOException {
    PDRectangle targetBox = targetPage.getMediaBox();
    // Stretch the normalised letterhead form onto the target page's own box, so a letterhead whose
    // page size or origin differs from the target's still covers exactly one page.
    float scaleX = targetBox.getWidth() / sourceBox.getWidth();
    float scaleY = targetBox.getHeight() / sourceBox.getHeight();
    Matrix placement =
        new Matrix(
            scaleX,
            0f,
            0f,
            scaleY,
            targetBox.getLowerLeftX() + surplusShiftX * scaleX,
            targetBox.getLowerLeftY() + surplusShiftY * scaleY);
    try (PDPageContentStream contentStream =
        new PDPageContentStream(
            targetDocument, targetPage, PDPageContentStream.AppendMode.PREPEND, true)) {
      contentStream.saveGraphicsState();
      contentStream.transform(placement);
      contentStream.drawForm(form);
      contentStream.restoreGraphicsState();
    }
  }

  private void overlayImageLetterhead(PDDocument targetDocument, Letterhead letterhead)
      throws IOException {
    PDImageXObject image =
        PDImageXObject.createFromByteArray(targetDocument, letterhead.content(), IMAGE_NAME);
    for (PDPage targetPage : targetDocument.getPages()) {
      PDRectangle targetBox = targetPage.getMediaBox();
      try (PDPageContentStream contentStream =
          new PDPageContentStream(
              targetDocument, targetPage, PDPageContentStream.AppendMode.PREPEND, true)) {
        contentStream.drawImage(
            image,
            targetBox.getLowerLeftX(),
            targetBox.getLowerLeftY(),
            targetBox.getWidth(),
            targetBox.getHeight());
      }
    }
  }
}
