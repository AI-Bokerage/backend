package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StandardDocumentTypeTest {

  @Test
  void constantsExposeExpectedCodes() {
    assertThat(StandardDocumentType.INVOICE.code()).isEqualTo("INVOICE");
    assertThat(StandardDocumentType.DUNNING_LETTER.code()).isEqualTo("DUNNING_LETTER");
  }

  @Test
  void ofBuildsCustomType() {
    DocumentType custom = StandardDocumentType.of("PURCHASE_ORDER");
    assertThat(custom.code()).isEqualTo("PURCHASE_ORDER");
  }

  @Test
  void equalsIsBasedOnCode() {
    assertThat(StandardDocumentType.of("INVOICE")).isEqualTo(StandardDocumentType.INVOICE);
  }

  @Test
  void ofRejectsBlankCode() {
    assertThatThrownBy(() -> StandardDocumentType.of(""))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }

  @Test
  void ofRejectsNullCode() {
    assertThatThrownBy(() -> StandardDocumentType.of(null))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }
}
