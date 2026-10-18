package io.genfin.document.api.model;

import io.genfin.api.validation.Validate;
import java.util.List;

/** The renderer-agnostic, in-memory representation of a document produced by a mapper. */
public final class DocumentModel {

  private final DocumentMetadata metadata;
  private final List<DocumentSection> sections;
  private final DocumentAttributes attributes;

  private DocumentModel(
      DocumentMetadata metadata, List<DocumentSection> sections, DocumentAttributes attributes) {
    this.metadata = Validate.notNull(metadata, "DocumentModel metadata must not be null");
    this.sections =
        List.copyOf(Validate.notNull(sections, "DocumentModel sections must not be null"));
    this.attributes = Validate.notNull(attributes, "DocumentModel attributes must not be null");
  }

  public static DocumentModel of(
      DocumentMetadata metadata, List<DocumentSection> sections, DocumentAttributes attributes) {
    return new DocumentModel(metadata, sections, attributes);
  }

  public DocumentMetadata metadata() {
    return metadata;
  }

  public List<DocumentSection> sections() {
    return sections;
  }

  public DocumentAttributes attributes() {
    return attributes;
  }
}
