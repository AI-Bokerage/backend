package io.genfin.document.api.letterhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardLetterheadOverlayPolicyTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardLetterheadOverlayPolicy.CONTENT_ON_TOP.code()).isEqualTo("CONTENT_ON_TOP");
    assertThat(StandardLetterheadOverlayPolicy.LETTERHEAD_ON_TOP.code())
        .isEqualTo("LETTERHEAD_ON_TOP");
  }

  @Test
  void ofRejectsNullCode() {
    assertThatThrownBy(() -> StandardLetterheadOverlayPolicy.of(null))
        .isInstanceOf(ValidationException.class);
  }
}
