package io.genfin.document.internal.renderer.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.brand.BrandIdentity;
import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.BrandId;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.qr.QrCodeContent;
import io.genfin.document.api.result.PdfRenderInputs;
import io.genfin.document.api.watermark.StandardWatermarkLabel;
import io.genfin.document.api.watermark.StandardWatermarkPlacement;
import io.genfin.document.api.watermark.Watermark;
import io.genfin.document.api.watermark.WatermarkOpacity;
import io.genfin.document.port.RendererConfiguration;
import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfRendererTest {

  @Test
  void rendersFullDocumentWithBrandWatermarkAndQr() throws Exception {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
            List.of(DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    BrandProfile brand =
        BrandProfile.builder(BrandId.of("b-1"), BrandIdentity.of("Acme Inc.", "addr")).build();
    Watermark watermark =
        Watermark.of(
            StandardWatermarkLabel.DRAFT,
            StandardWatermarkPlacement.DIAGONAL_CENTER,
            WatermarkOpacity.DEFAULT);
    QrCodeContent qr = QrCodeContent.of(tinyPng(), "image/png");
    PdfRenderInputs inputs =
        PdfRenderInputs.builder(
                PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build())
            .brandProfile(brand)
            .watermark(watermark)
            .qrCode("payment-link", qr)
            .build();

    PdfRenderer renderer = new PdfRenderer();
    var result = renderer.render(document, RendererConfiguration.defaults().withPdfInputs(inputs));

    assertThat(result.mimeType()).isEqualTo("application/pdf");
    try (PDDocument loaded = Loader.loadPDF(result.content())) {
      String text = new PDFTextStripper().getText(loaded);
      assertThat(text).contains("Total");
      assertThat(text).contains("500.00");
      assertThat(text).contains("DRAFT");
    }
  }

  @Test
  void idAndCapabilitiesArePdf() {
    PdfRenderer renderer = new PdfRenderer();
    assertThat(renderer.id().toString()).contains("pdf");
    assertThat(renderer.capabilities().mimeType()).isEqualTo("application/pdf");
  }

  private static byte[] tinyPng() throws Exception {
    java.awt.image.BufferedImage image =
        new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
