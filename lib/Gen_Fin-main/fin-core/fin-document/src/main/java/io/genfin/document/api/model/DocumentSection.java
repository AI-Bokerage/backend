package io.genfin.document.api.model;

import io.genfin.api.validation.Validate;
import java.util.List;

/** A titled group of {@link DocumentElement}s within a {@link DocumentModel}. */
public final class DocumentSection {

  private final String title;
  private final List<DocumentElement> elements;

  private DocumentSection(String title, List<DocumentElement> elements) {
    this.title = Validate.notNull(title, "DocumentSection title must not be null");
    this.elements =
        List.copyOf(Validate.notNull(elements, "DocumentSection elements must not be null"));
  }

  public static DocumentSection of(String title, List<DocumentElement> elements) {
    return new DocumentSection(title, elements);
  }

  public String title() {
    return title;
  }

  public List<DocumentElement> elements() {
    return elements;
  }
}
