package io.genfin.document.api.event;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.LetterheadId;
import java.time.Instant;

/** Fired when a letterhead is applied to a document. */
public final class LetterheadApplied implements DocumentEvent {

  private final DocumentId documentId;
  private final LetterheadId letterheadId;
  private final Instant occurredAt;

  private LetterheadApplied(DocumentId documentId, LetterheadId letterheadId) {
    this.documentId = Validate.notNull(documentId, "documentId must not be null");
    this.letterheadId = Validate.notNull(letterheadId, "letterheadId must not be null");
    this.occurredAt = Instant.now();
  }

  public static LetterheadApplied of(DocumentId documentId, LetterheadId letterheadId) {
    return new LetterheadApplied(documentId, letterheadId);
  }

  public DocumentId documentId() {
    return documentId;
  }

  public LetterheadId letterheadId() {
    return letterheadId;
  }

  @Override
  public Instant occurredAt() {
    return occurredAt;
  }
}
