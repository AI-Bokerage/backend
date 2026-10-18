package io.genfin.document.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.BrandId;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.qr.QrCodePlacement;
import io.genfin.document.api.qr.StandardQrCodePlacement;
import io.genfin.document.api.watermark.WatermarkLabel;
import org.junit.jupiter.api.Test;

class DocumentEventTest {

  @Test
  void documentRenderedExposesFieldsAndTimestamp() {
    DocumentRendered event = DocumentRendered.of(RendererId.of("pdf"), DocumentId.of("doc-1"));

    assertThat(event.rendererId()).isEqualTo(RendererId.of("pdf"));
    assertThat(event.documentId()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void pdfGeneratedExposesPageCount() {
    PdfGenerated event = PdfGenerated.of(DocumentId.of("doc-1"), 3);
    assertThat(event.pageCount()).isEqualTo(3);
  }

  @Test
  void brandAppliedExposesFields() {
    BrandApplied event = BrandApplied.of(DocumentId.of("doc-1"), BrandId.of("brand-1"));

    assertThat(event.documentId()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(event.brandId()).isEqualTo(BrandId.of("brand-1"));
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void letterheadAppliedExposesFields() {
    LetterheadApplied event = LetterheadApplied.of(DocumentId.of("doc-1"), LetterheadId.of("lh-1"));

    assertThat(event.documentId()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(event.letterheadId()).isEqualTo(LetterheadId.of("lh-1"));
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void layoutAppliedExposesFields() {
    PageLayout layout =
        PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
    LayoutApplied event = LayoutApplied.of(DocumentId.of("doc-1"), layout);

    assertThat(event.documentId()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(event.pageLayout()).isEqualTo(layout);
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void watermarkAppliedExposesFields() {
    WatermarkLabel label = () -> "CONFIDENTIAL";
    WatermarkApplied event = WatermarkApplied.of(DocumentId.of("doc-1"), label);

    assertThat(event.documentId()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(event.label()).isEqualTo(label);
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void qrCodeRenderedExposesFields() {
    QrCodePlacement placement = StandardQrCodePlacement.BOTTOM_RIGHT;
    QrCodeRendered event = QrCodeRendered.of(DocumentId.of("doc-1"), placement);

    assertThat(event.documentId()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(event.placement()).isEqualTo(placement);
    assertThat(event.occurredAt()).isNotNull();
  }
}
