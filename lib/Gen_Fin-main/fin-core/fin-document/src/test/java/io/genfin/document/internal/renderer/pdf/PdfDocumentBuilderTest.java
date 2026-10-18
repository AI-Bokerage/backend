package io.genfin.document.internal.renderer.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.layout.Margins;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

class PdfDocumentBuilderTest {

  @Test
  void portraitA4PageHasA4Dimensions() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
    PdfDocumentBuilder builder = new PdfDocumentBuilder(layout);

    PDPage page = builder.addPage();

    assertThat(page.getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
    assertThat(page.getMediaBox().getHeight()).isEqualTo(PDRectangle.A4.getHeight());
  }

  @Test
  void landscapeA4PageHasSwappedDimensions() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.LANDSCAPE).build();
    PdfDocumentBuilder builder = new PdfDocumentBuilder(layout);

    PDPage page = builder.addPage();

    assertThat(page.getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getHeight());
    assertThat(page.getMediaBox().getHeight()).isEqualTo(PDRectangle.A4.getWidth());
  }

  @Test
  void contentBoxAccountsForMargins() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT)
            .margins(Margins.uniform(36))
            .build();
    PdfDocumentBuilder builder = new PdfDocumentBuilder(layout);
    builder.addPage();

    assertThat(builder.contentLeft()).isEqualTo(36f);
    assertThat(builder.contentTop()).isEqualTo(PDRectangle.A4.getHeight() - 36f);
    assertThat(builder.contentBottom()).isEqualTo(36f);
    assertThat(builder.contentWidth()).isEqualTo(PDRectangle.A4.getWidth() - 72f);
  }

  @Test
  void addingASecondPageIncreasesDocumentPageCount() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
    PdfDocumentBuilder builder = new PdfDocumentBuilder(layout);

    builder.addPage();
    builder.addPage();

    assertThat(builder.document().getNumberOfPages()).isEqualTo(2);
  }
}
