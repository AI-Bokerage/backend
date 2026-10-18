package io.genfin.api.id;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentifierTest {

  @Test
  void sameValueDifferentTypesAreNotEqual() {
    CorrelationId correlationId = CorrelationId.of("abc");
    RequestId requestId = RequestId.of("abc");

    assertThat(correlationId).isNotEqualTo(requestId);
  }

  @Test
  void sameTypeAndValueAreEqual() {
    assertThat(CorrelationId.of("abc")).isEqualTo(CorrelationId.of("abc"));
    assertThat(CorrelationId.of("abc").hashCode()).isEqualTo(CorrelationId.of("abc").hashCode());
  }

  @Test
  void generateProducesUniqueNonBlankValues() {
    TransactionId first = TransactionId.generate();
    TransactionId second = TransactionId.generate();

    assertThat(first).isNotEqualTo(second);
    assertThat(first.value()).isNotBlank();
  }

  @Test
  void blankValueIsRejected() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> ReferenceId.of(" "))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }
}
