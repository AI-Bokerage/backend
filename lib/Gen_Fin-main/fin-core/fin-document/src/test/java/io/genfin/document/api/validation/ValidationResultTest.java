package io.genfin.document.api.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

  @Test
  void validResultHasNoIssues() {
    ValidationResult result = ValidationResult.valid();
    assertThat(result.isValid()).isTrue();
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void resultWithIssuesIsInvalid() {
    ValidationResult result =
        ValidationResult.withIssues(
            List.of(ValidationIssue.of("RENDERER_NOT_FOUND", "no such renderer")));
    assertThat(result.isValid()).isFalse();
    assertThat(result.issues()).hasSize(1);
    assertThat(result.issues().get(0).code()).isEqualTo("RENDERER_NOT_FOUND");
  }
}
