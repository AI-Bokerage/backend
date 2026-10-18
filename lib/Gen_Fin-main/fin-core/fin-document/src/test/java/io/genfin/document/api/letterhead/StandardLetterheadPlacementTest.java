package io.genfin.document.api.letterhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardLetterheadPlacementTest {

  @Test
  void fullPageConstantExposesExpectedCode() {
    assertThat(StandardLetterheadPlacement.FULL_PAGE.code()).isEqualTo("FULL_PAGE");
  }

  @Test
  void ofBuildsCustomPlacement() {
    assertThat(StandardLetterheadPlacement.of("HEADER_ONLY").code()).isEqualTo("HEADER_ONLY");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardLetterheadPlacement.of(" "))
        .isInstanceOf(ValidationException.class);
  }
}
