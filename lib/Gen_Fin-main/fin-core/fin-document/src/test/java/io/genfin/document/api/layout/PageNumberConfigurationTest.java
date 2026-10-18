package io.genfin.document.api.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class PageNumberConfigurationTest {

  @Test
  void disabledHasEmptyFormat() {
    PageNumberConfiguration config = PageNumberConfiguration.disabled();
    assertThat(config.enabled()).isFalse();
    assertThat(config.format()).isEmpty();
  }

  @Test
  void enabledExposesGivenFormat() {
    PageNumberConfiguration config = PageNumberConfiguration.enabled("Page {n} of {total}");
    assertThat(config.enabled()).isTrue();
    assertThat(config.format()).isEqualTo("Page {n} of {total}");
  }

  @Test
  void enabledWithNullFormatThrowsValidationException() {
    assertThatThrownBy(() -> PageNumberConfiguration.enabled(null))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void enabledWithBlankFormatThrowsValidationException() {
    assertThatThrownBy(() -> PageNumberConfiguration.enabled(""))
        .isInstanceOf(ValidationException.class);
  }
}
