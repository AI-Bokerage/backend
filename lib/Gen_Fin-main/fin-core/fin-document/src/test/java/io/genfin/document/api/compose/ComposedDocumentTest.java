package io.genfin.document.api.compose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.StandardDocumentType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComposedDocumentTest {

  private static DocumentMetadata metadata() {
    return DocumentMetadata.of(
        DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);
  }

  @Test
  void ofExposesGivenValues() {
    ComposedDocument document =
        ComposedDocument.of(metadata(), List.of(), DocumentAttributes.empty());
    assertThat(document.metadata().id()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(document.sections()).isEmpty();
  }

  @Test
  void ofRejectsNullMetadata() {
    assertThatThrownBy(() -> ComposedDocument.of(null, List.of(), DocumentAttributes.empty()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullSections() {
    assertThatThrownBy(() -> ComposedDocument.of(metadata(), null, DocumentAttributes.empty()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullAttributes() {
    assertThatThrownBy(() -> ComposedDocument.of(metadata(), List.of(), null))
        .isInstanceOf(ValidationException.class);
  }
}
