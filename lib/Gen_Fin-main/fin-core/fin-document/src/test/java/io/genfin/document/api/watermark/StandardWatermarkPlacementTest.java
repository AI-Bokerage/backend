package io.genfin.document.api.watermark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardWatermarkPlacementTest {

  @Test
  void diagonalCenterConstantExposesExpectedCode() {
    assertThat(StandardWatermarkPlacement.DIAGONAL_CENTER.code()).isEqualTo("DIAGONAL_CENTER");
  }

  @Test
  void ofRejectsNullCode() {
    assertThatThrownBy(() -> StandardWatermarkPlacement.of(null))
        .isInstanceOf(ValidationException.class);
  }
}
