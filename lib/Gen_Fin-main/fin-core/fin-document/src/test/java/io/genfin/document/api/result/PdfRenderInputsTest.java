package io.genfin.document.api.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.brand.BrandIdentity;
import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.identity.BrandId;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.letterhead.StandardLetterheadFormat;
import io.genfin.document.api.qr.QrCodeContent;
import io.genfin.document.api.watermark.StandardWatermarkLabel;
import io.genfin.document.api.watermark.StandardWatermarkPlacement;
import io.genfin.document.api.watermark.Watermark;
import io.genfin.document.api.watermark.WatermarkOpacity;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfRenderInputsTest {

  private static PageLayout layout() {
    return PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build();
  }

  @Test
  void buildsWithOnlyRequiredLayoutAndDefaultsTheRest() {
    PageLayout layout = layout();
    PdfRenderInputs inputs = PdfRenderInputs.builder(layout).build();

    assertThat(inputs.layout()).isSameAs(layout);
    assertThat(inputs.brandProfile()).isEmpty();
    assertThat(inputs.letterhead()).isEmpty();
    assertThat(inputs.watermark()).isEmpty();
    assertThat(inputs.qrCodes()).isEmpty();
  }

  @Test
  void buildsWithAllFieldsSet() {
    BrandProfile brand =
        BrandProfile.builder(BrandId.of("b-1"), BrandIdentity.of("Acme", "addr")).build();
    Letterhead letterhead =
        Letterhead.of(
            LetterheadId.of("lh-1"),
            StandardLetterheadFormat.PDF,
            "pdf-bytes".getBytes(StandardCharsets.UTF_8),
            "application/pdf");
    Watermark watermark =
        Watermark.of(
            StandardWatermarkLabel.DRAFT,
            StandardWatermarkPlacement.DIAGONAL_CENTER,
            WatermarkOpacity.DEFAULT);
    QrCodeContent qr = QrCodeContent.of("qr-bytes".getBytes(StandardCharsets.UTF_8), "image/png");

    PdfRenderInputs inputs =
        PdfRenderInputs.builder(layout())
            .brandProfile(brand)
            .letterhead(letterhead)
            .watermark(watermark)
            .qrCode("payment-link", qr)
            .build();

    assertThat(inputs.brandProfile()).contains(brand);
    assertThat(inputs.letterhead()).contains(letterhead);
    assertThat(inputs.watermark()).contains(watermark);
    assertThat(inputs.qrCodes()).containsEntry("payment-link", qr);
  }

  @Test
  void nullLayoutThrowsValidationException() {
    assertThatThrownBy(() -> PdfRenderInputs.builder(null).build())
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void explicitNullOptionalSettersCoalesceToEmptyRatherThanNull() {
    PdfRenderInputs inputs =
        PdfRenderInputs.builder(layout())
            .brandProfile(null)
            .letterhead(null)
            .watermark(null)
            .build();

    assertThat(inputs.brandProfile()).isEmpty();
    assertThat(inputs.letterhead()).isEmpty();
    assertThat(inputs.watermark()).isEmpty();
  }
}
