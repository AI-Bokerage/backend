package io.genfin.document.internal.renderer.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.watermark.StandardWatermarkLabel;
import io.genfin.document.api.watermark.StandardWatermarkPlacement;
import io.genfin.document.api.watermark.Watermark;
import io.genfin.document.api.watermark.WatermarkOpacity;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfWatermarkStamperTest {

  @Test
  void stampsWatermarkLabelTextOntoThePage() throws Exception {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
    PdfDocumentBuilder documentBuilder = new PdfDocumentBuilder(layout);
    PDPage page = documentBuilder.addPage();
    PdfWatermarkStamper stamper = new PdfWatermarkStamper(documentBuilder);

    Watermark watermark =
        Watermark.of(
            StandardWatermarkLabel.DRAFT,
            StandardWatermarkPlacement.DIAGONAL_CENTER,
            WatermarkOpacity.DEFAULT);
    stamper.stamp(page, watermark);

    String text = new PDFTextStripper().getText(documentBuilder.document());
    assertThat(text).contains("DRAFT");
  }
}
