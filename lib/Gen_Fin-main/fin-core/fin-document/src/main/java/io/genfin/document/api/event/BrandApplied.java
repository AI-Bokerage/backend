package io.genfin.document.api.event;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.BrandId;
import io.genfin.document.api.identity.DocumentId;
import java.time.Instant;

/** Fired when a brand identity is applied to a document. */
public final class BrandApplied implements DocumentEvent {

  private final DocumentId documentId;
  private final BrandId brandId;
  private final Instant occurredAt;

  private BrandApplied(DocumentId documentId, BrandId brandId) {
    this.documentId = Validate.notNull(documentId, "documentId must not be null");
    this.brandId = Validate.notNull(brandId, "brandId must not be null");
    this.occurredAt = Instant.now();
  }

  public static BrandApplied of(DocumentId documentId, BrandId brandId) {
    return new BrandApplied(documentId, brandId);
  }

  public DocumentId documentId() {
    return documentId;
  }

  public BrandId brandId() {
    return brandId;
  }

  @Override
  public Instant occurredAt() {
    return occurredAt;
  }
}
