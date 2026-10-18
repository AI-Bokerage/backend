package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class MarginsTest {

  @Test
  void ofExposesGivenValues() {
    Margins margins = Margins.of(10, 20, 30, 40);
    assertThat(margins.topPoints()).isEqualTo(10);
    assertThat(margins.bottomPoints()).isEqualTo(20);
    assertThat(margins.leftPoints()).isEqualTo(30);
    assertThat(margins.rightPoints()).isEqualTo(40);
  }

  @Test
  void uniformAppliesSameValueToAllSides() {
    Margins margins = Margins.uniform(15);
    assertThat(margins.topPoints()).isEqualTo(15);
    assertThat(margins.bottomPoints()).isEqualTo(15);
    assertThat(margins.leftPoints()).isEqualTo(15);
    assertThat(margins.rightPoints()).isEqualTo(15);
  }

  @Test
  void noneIsAllZero() {
    Margins margins = Margins.none();
    assertThat(margins.topPoints()).isZero();
    assertThat(margins.bottomPoints()).isZero();
    assertThat(margins.leftPoints()).isZero();
    assertThat(margins.rightPoints()).isZero();
  }

  @Test
  void rejectsNegativeTop() {
    assertThatThrownBy(() -> Margins.of(-1, 0, 0, 0)).isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNegativeBottom() {
    assertThatThrownBy(() -> Margins.of(0, -1, 0, 0)).isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNegativeLeft() {
    assertThatThrownBy(() -> Margins.of(0, 0, -1, 0)).isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNegativeRight() {
    assertThatThrownBy(() -> Margins.of(0, 0, 0, -1)).isInstanceOf(ValidationException.class);
  }
}
