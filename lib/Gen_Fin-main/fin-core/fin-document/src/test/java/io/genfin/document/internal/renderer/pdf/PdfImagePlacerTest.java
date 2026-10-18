package io.genfin.document.internal.renderer.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.layout.Margins;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

class PdfImagePlacerTest {

  private static final float LOGO_SIZE = 60f;
  private static final float QR_SIZE = 80f;

  private static byte[] tinyPng() throws Exception {
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  @Test
  void drawingLogoAddsAnImageXObjectToThePage() throws Exception {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout);
    PDPage page = documentBuilder.addPage();
    PdfImagePlacer placer = new PdfImagePlacer(documentBuilder);

    placer.drawTopLeft(page, tinyPng());

    assertThat(page.getResources().getXObjectNames()).isNotEmpty();
  }

  @Test
  void drawingQrCodeAddsAnImageXObjectToThePage() throws Exception {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout);
    PDPage page = documentBuilder.addPage();
    PdfImagePlacer placer = new PdfImagePlacer(documentBuilder);

    placer.drawBottomRight(page, tinyPng());

    assertThat(page.getResources().getXObjectNames()).isNotEmpty();
  }

  @Test
  void drawTopLeftPlacesTheActualImageDrawOperatorWithinPageBounds() throws Exception {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT)
            .margins(Margins.uniform(36))
            .build();
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout);
    PDPage page = documentBuilder.addPage();
    PdfImagePlacer placer = new PdfImagePlacer(documentBuilder);

    placer.drawTopLeft(page, tinyPng());

    assertImageDrawIsWithinPageBounds(page, LOGO_SIZE);
  }

  @Test
  void drawBottomRightPlacesTheActualImageDrawOperatorWithinPageBounds() throws Exception {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT)
            .margins(Margins.uniform(36))
            .build();
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout);
    PDPage page = documentBuilder.addPage();
    PdfImagePlacer placer = new PdfImagePlacer(documentBuilder);

    placer.drawBottomRight(page, tinyPng());

    assertImageDrawIsWithinPageBounds(page, QR_SIZE);
  }

  // Replays the page's real content stream and captures the CTM in effect at the actual "Do"
  // (draw XObject) operator emitted by the SUT, rather than recomputing expected coordinates
  // independently. This is what would catch a sign error or wrong-accessor bug in PdfImagePlacer.
  private static void assertImageDrawIsWithinPageBounds(PDPage page, float expectedSize)
      throws IOException {
    ImageDrawCapture capture = new ImageDrawCapture();
    capture.processPage(page);
    Matrix matrix = capture.lastImageMatrix;

    assertThat(matrix).isNotNull();
    float x = matrix.getTranslateX();
    float y = matrix.getTranslateY();
    float width = matrix.getScaleX();
    float height = matrix.getScaleY();
    float pageWidth = page.getMediaBox().getWidth();
    float pageHeight = page.getMediaBox().getHeight();

    assertThat(width).isEqualTo(expectedSize);
    assertThat(height).isEqualTo(expectedSize);
    assertThat(x).isGreaterThanOrEqualTo(0f);
    assertThat(x + width).isLessThanOrEqualTo(pageWidth);
    assertThat(y).isGreaterThanOrEqualTo(0f);
    assertThat(y + height).isLessThanOrEqualTo(pageHeight);
  }

  private static final class ImageDrawCapture extends PDFStreamEngine {

    private static final String DRAW_OBJECT_OPERATOR = "Do";

    private Matrix lastImageMatrix;

    ImageDrawCapture() {
      addOperator(new Concatenate(this));
      addOperator(new Save(this));
      addOperator(new Restore(this));
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
      super.processOperator(operator, operands);
      if (DRAW_OBJECT_OPERATOR.equals(operator.getName())) {
        lastImageMatrix = getGraphicsState().getCurrentTransformationMatrix();
      }
    }
  }
}
