package io.genfin.document.api.letterhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardLetterheadFormatTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardLetterheadFormat.PDF.code()).isEqualTo("PDF");
    assertThat(StandardLetterheadFormat.PNG.code()).isEqualTo("PNG");
    assertThat(StandardLetterheadFormat.JPEG.code()).isEqualTo("JPEG");
    assertThat(StandardLetterheadFormat.SVG.code()).isEqualTo("SVG");
    assertThat(StandardLetterheadFormat.BLANK.code()).isEqualTo("BLANK");
  }

  @Test
  void ofBuildsCustomFormat() {
    assertThat(StandardLetterheadFormat.of("WEBP").code()).isEqualTo("WEBP");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardLetterheadFormat.of(""))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullCode() {
    assertThatThrownBy(() -> StandardLetterheadFormat.of(null))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void equalsIsBasedOnCode() {
    assertThat(StandardLetterheadFormat.of("PDF")).isEqualTo(StandardLetterheadFormat.PDF);
  }
}
