package io.genfin.document.api.event;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.DocumentId;
import java.time.Instant;

/** Fired when a PDF is successfully generated with a specific page count. */
public final class PdfGenerated implements DocumentEvent {

  private final DocumentId documentId;
  private final int pageCount;
  private final Instant occurredAt;

  private PdfGenerated(DocumentId documentId, int pageCount) {
    this.documentId = Validate.notNull(documentId, "documentId must not be null");
    this.pageCount = Validate.positive(pageCount, "pageCount must be positive");
    this.occurredAt = Instant.now();
  }

  public static PdfGenerated of(DocumentId documentId, int pageCount) {
    return new PdfGenerated(documentId, pageCount);
  }

  public DocumentId documentId() {
    return documentId;
  }

  public int pageCount() {
    return pageCount;
  }

  @Override
  public Instant occurredAt() {
    return occurredAt;
  }
}
