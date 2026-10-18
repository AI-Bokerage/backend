package io.genfin.document.api.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardQrCodePlacementTest {

  @Test
  void bottomRightConstantExposesExpectedCode() {
    assertThat(StandardQrCodePlacement.BOTTOM_RIGHT.code()).isEqualTo("BOTTOM_RIGHT");
  }

  @Test
  void ofBuildsCustomPlacement() {
    assertThat(StandardQrCodePlacement.of("TOP_LEFT").code()).isEqualTo("TOP_LEFT");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardQrCodePlacement.of(""))
        .isInstanceOf(ValidationException.class);
  }
}
