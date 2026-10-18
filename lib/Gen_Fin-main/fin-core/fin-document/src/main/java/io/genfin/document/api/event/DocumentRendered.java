package io.genfin.document.api.event;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.RendererId;
import java.time.Instant;

/** Fired when a document is successfully rendered to a specific format. */
public final class DocumentRendered implements DocumentEvent {

  private final RendererId rendererId;
  private final DocumentId documentId;
  private final Instant occurredAt;

  private DocumentRendered(RendererId rendererId, DocumentId documentId) {
    this.rendererId = Validate.notNull(rendererId, "rendererId must not be null");
    this.documentId = Validate.notNull(documentId, "documentId must not be null");
    this.occurredAt = Instant.now();
  }

  public static DocumentRendered of(RendererId rendererId, DocumentId documentId) {
    return new DocumentRendered(rendererId, documentId);
  }

  public RendererId rendererId() {
    return rendererId;
  }

  public DocumentId documentId() {
    return documentId;
  }

  @Override
  public Instant occurredAt() {
    return occurredAt;
  }
}
