package io.genfin.document.api.watermark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class WatermarkOpacityTest {

  @Test
  void exposesGivenValue() {
    assertThat(WatermarkOpacity.of(0.5).value()).isEqualTo(0.5);
  }

  @Test
  void defaultConstantIsPointThree() {
    assertThat(WatermarkOpacity.DEFAULT.value()).isEqualTo(0.3);
  }

  @Test
  void boundaryValuesAreAccepted() {
    assertThat(WatermarkOpacity.of(0.0).value()).isEqualTo(0.0);
    assertThat(WatermarkOpacity.of(1.0).value()).isEqualTo(1.0);
  }

  @Test
  void belowZeroIsRejected() {
    assertThatThrownBy(() -> WatermarkOpacity.of(-0.01)).isInstanceOf(ValidationException.class);
  }

  @Test
  void aboveOneIsRejected() {
    assertThatThrownBy(() -> WatermarkOpacity.of(1.01)).isInstanceOf(ValidationException.class);
  }
}
