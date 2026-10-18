package io.genfin.document.api.watermark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class StandardWatermarkLabelTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardWatermarkLabel.DRAFT.code()).isEqualTo("DRAFT");
    assertThat(StandardWatermarkLabel.PAID.code()).isEqualTo("PAID");
    assertThat(StandardWatermarkLabel.VOID.code()).isEqualTo("VOID");
    assertThat(StandardWatermarkLabel.COPY.code()).isEqualTo("COPY");
    assertThat(StandardWatermarkLabel.CONFIDENTIAL.code()).isEqualTo("CONFIDENTIAL");
  }

  @Test
  void ofBuildsCustomLabel() {
    assertThat(StandardWatermarkLabel.of("SAMPLE").code()).isEqualTo("SAMPLE");
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardWatermarkLabel.of("")).isInstanceOf(ValidationException.class);
  }
}
