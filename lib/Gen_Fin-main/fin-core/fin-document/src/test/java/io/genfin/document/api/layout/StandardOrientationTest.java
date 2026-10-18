package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardOrientationTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardOrientation.PORTRAIT.code()).isEqualTo("PORTRAIT");
    assertThat(StandardOrientation.LANDSCAPE.code()).isEqualTo("LANDSCAPE");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardOrientation.of(" ")).isInstanceOf(ValidationException.class);
  }
}
