package io.genfin.api.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class ValidateTest {

  @Test
  void notNullReturnsValueWhenPresent() {
    assertThat(Validate.notNull("x", "must not be null")).isEqualTo("x");
  }

  @Test
  void notNullThrowsWhenAbsent() {
    assertThatThrownBy(() -> Validate.notNull(null, "must not be null"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void notBlankRejectsBlankStrings() {
    assertThatThrownBy(() -> Validate.notBlank("  ", "blank"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void positiveRejectsZeroAndNegative() {
    assertThatThrownBy(() -> Validate.positive(0, "must be positive"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Validate.positive(-1, "must be positive"))
        .isInstanceOf(ValidationException.class);
    assertThat(Validate.positive(1, "must be positive")).isEqualTo(1);
  }

  @Test
  void nonNegativeAllowsZero() {
    assertThat(Validate.nonNegative(0, "must be non-negative")).isZero();
    assertThatThrownBy(() -> Validate.nonNegative(-1, "must be non-negative"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void stateAndArgumentThrowDistinctExceptionTypes() {
    assertThatThrownBy(() -> Validate.state(false, "bad state"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> Validate.argument(false, "bad argument"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
