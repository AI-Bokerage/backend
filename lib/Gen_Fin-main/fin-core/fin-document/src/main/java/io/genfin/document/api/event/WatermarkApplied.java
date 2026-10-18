package io.genfin.document.api.event;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.watermark.WatermarkLabel;
import java.time.Instant;

/** Fired when a watermark is applied to a document. */
public final class WatermarkApplied implements DocumentEvent {

  private final DocumentId documentId;
  private final WatermarkLabel label;
  private final Instant occurredAt;

  private WatermarkApplied(DocumentId documentId, WatermarkLabel label) {
    this.documentId = Validate.notNull(documentId, "documentId must not be null");
    this.label = Validate.notNull(label, "label must not be null");
    this.occurredAt = Instant.now();
  }

  public static WatermarkApplied of(DocumentId documentId, WatermarkLabel label) {
    return new WatermarkApplied(documentId, label);
  }

  public DocumentId documentId() {
    return documentId;
  }

  public WatermarkLabel label() {
    return label;
  }

  @Override
  public Instant occurredAt() {
    return occurredAt;
  }
}
