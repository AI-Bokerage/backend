package io.genfin.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GenFinExceptionTest {

  @Test
  void carriesErrorCodeAndImmutableMetadata() {
    var metadata = Map.<String, Object>of("field", "amount");
    var exception = new ValidationException("bad amount", metadata);

    assertThat(exception.errorCode()).isEqualTo(CoreErrorCode.VALIDATION_FAILED);
    assertThat(exception.metadata()).isEqualTo(metadata);
    assertThatThrownBy(() -> exception.metadata().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void supportsNestedCause() {
    var cause = new IllegalStateException("root cause");
    var exception = new IntegrationException("downstream failed", cause);

    assertThat(exception.getCause()).isSameAs(cause);
    assertThat(exception.errorCode()).isEqualTo(CoreErrorCode.INTEGRATION_FAILURE);
  }
}
