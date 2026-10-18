package io.genfin.document.api.watermark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class WatermarkTest {

  @Test
  void exposesGivenFields() {
    Watermark watermark =
        Watermark.of(
            StandardWatermarkLabel.DRAFT,
            StandardWatermarkPlacement.DIAGONAL_CENTER,
            WatermarkOpacity.DEFAULT);

    assertThat(watermark.label()).isEqualTo(StandardWatermarkLabel.DRAFT);
    assertThat(watermark.placement()).isEqualTo(StandardWatermarkPlacement.DIAGONAL_CENTER);
    assertThat(watermark.opacity()).isEqualTo(WatermarkOpacity.DEFAULT);
  }

  @Test
  void nullLabelThrowsValidationException() {
    assertThatThrownBy(
            () ->
                Watermark.of(
                    null, StandardWatermarkPlacement.DIAGONAL_CENTER, WatermarkOpacity.DEFAULT))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullPlacementThrowsValidationException() {
    assertThatThrownBy(
            () -> Watermark.of(StandardWatermarkLabel.DRAFT, null, WatermarkOpacity.DEFAULT))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullOpacityThrowsValidationException() {
    assertThatThrownBy(
            () ->
                Watermark.of(
                    StandardWatermarkLabel.DRAFT, StandardWatermarkPlacement.DIAGONAL_CENTER, null))
        .isInstanceOf(ValidationException.class);
  }
}
