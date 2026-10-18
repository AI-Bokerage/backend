package io.genfin.document.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocumentIdTest {

  @Test
  void ofReturnsIdWithGivenValue() {
    DocumentId id = DocumentId.of("doc-123");
    assertThat(id.value()).isEqualTo("doc-123");
  }

  @Test
  void generateReturnsIdWithNonBlankValue() {
    DocumentId id = DocumentId.generate();
    assertThat(id.value()).isNotBlank();
  }

  @Test
  void ofRejectsBlankValue() {
    assertThatThrownBy(() -> DocumentId.of(" "))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }

  @Test
  void equalsIsBasedOnTypeAndValue() {
    assertThat(DocumentId.of("doc-1")).isEqualTo(DocumentId.of("doc-1"));
  }
}
