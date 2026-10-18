package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardPaperSizeTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardPaperSize.A4.code()).isEqualTo("A4");
    assertThat(StandardPaperSize.LETTER.code()).isEqualTo("LETTER");
  }

  @Test
  void ofBuildsCustomPaperSize() {
    assertThat(StandardPaperSize.of("LEGAL").code()).isEqualTo("LEGAL");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardPaperSize.of("")).isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullCode() {
    assertThatThrownBy(() -> StandardPaperSize.of(null)).isInstanceOf(ValidationException.class);
  }

  @Test
  void equalsIsBasedOnCode() {
    assertThat(StandardPaperSize.of("A4")).isEqualTo(StandardPaperSize.A4);
  }
}
