package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.DocumentId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentModelTest {

  @Test
  void modelExposesMetadataSectionsAndAttributes() {
    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);
    DocumentSection section =
        DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")));
    DocumentAttributes attributes = DocumentAttributes.of(java.util.Map.of("region", "IN"));

    DocumentModel model = DocumentModel.of(metadata, List.of(section), attributes);

    assertThat(model.metadata().id()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(model.metadata().type()).isEqualTo(StandardDocumentType.INVOICE);
    assertThat(model.sections()).hasSize(1);
    assertThat(model.sections().get(0).title()).isEqualTo("Summary");
    assertThat(model.attributes().get("region")).contains("IN");
  }

  @Test
  void sectionsListIsImmutable() {
    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);
    DocumentModel model = DocumentModel.of(metadata, List.of(), DocumentAttributes.empty());

    assertThat(model.sections()).isUnmodifiable();
  }

  @Test
  void ofRejectsNullMetadata() {
    assertThatThrownBy(() -> DocumentModel.of(null, List.of(), DocumentAttributes.empty()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullSections() {
    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);
    assertThatThrownBy(() -> DocumentModel.of(metadata, null, DocumentAttributes.empty()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullAttributes() {
    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);
    assertThatThrownBy(() -> DocumentModel.of(metadata, List.of(), null))
        .isInstanceOf(ValidationException.class);
  }
}
