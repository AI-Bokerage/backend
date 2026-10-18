package io.genfin.document.api.compose;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import java.util.List;

/**
 * The fully composed, renderer-ready form of a document, produced by a {@link DocumentComposer}.
 */
public final class ComposedDocument {

  private final DocumentMetadata metadata;
  private final List<DocumentSection> sections;
  private final DocumentAttributes attributes;

  private ComposedDocument(
      DocumentMetadata metadata, List<DocumentSection> sections, DocumentAttributes attributes) {
    this.metadata = Validate.notNull(metadata, "ComposedDocument metadata must not be null");
    this.sections =
        List.copyOf(Validate.notNull(sections, "ComposedDocument sections must not be null"));
    this.attributes = Validate.notNull(attributes, "ComposedDocument attributes must not be null");
  }

  public static ComposedDocument of(
      DocumentMetadata metadata, List<DocumentSection> sections, DocumentAttributes attributes) {
    return new ComposedDocument(metadata, sections, attributes);
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
