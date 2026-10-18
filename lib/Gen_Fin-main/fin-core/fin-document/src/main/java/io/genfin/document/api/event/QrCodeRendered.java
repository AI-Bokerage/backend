package io.genfin.document.api.event;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.qr.QrCodePlacement;
import java.time.Instant;

/** Fired when a QR code is rendered onto a document. */
public final class QrCodeRendered implements DocumentEvent {

  private final DocumentId documentId;
  private final QrCodePlacement placement;
  private final Instant occurredAt;

  private QrCodeRendered(DocumentId documentId, QrCodePlacement placement) {
    this.documentId = Validate.notNull(documentId, "documentId must not be null");
    this.placement = Validate.notNull(placement, "placement must not be null");
    this.occurredAt = Instant.now();
  }

  public static QrCodeRendered of(DocumentId documentId, QrCodePlacement placement) {
    return new QrCodeRendered(documentId, placement);
  }

  public DocumentId documentId() {
    return documentId;
  }

  public QrCodePlacement placement() {
    return placement;
  }

  @Override
  public Instant occurredAt() {
    return occurredAt;
  }
}
