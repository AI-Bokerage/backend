package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.DocumentId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DocumentMetadataTest {

  @Test
  void ofExposesGivenValues() {
    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);

    assertThat(metadata.id()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(metadata.type()).isEqualTo(StandardDocumentType.INVOICE);
  }

  @Test
  void ofRejectsNullId() {
    assertThatThrownBy(
            () ->
                DocumentMetadata.of(
                    null, StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullType() {
    assertThatThrownBy(
            () -> DocumentMetadata.of(DocumentId.of("doc-1"), null, "en-IN", "INR", Instant.EPOCH))
        .isInstanceOf(ValidationException.class);
  }
}
