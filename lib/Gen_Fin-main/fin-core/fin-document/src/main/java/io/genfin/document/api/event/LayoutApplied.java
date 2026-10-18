package io.genfin.document.api.event;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.layout.PageLayout;
import java.time.Instant;

/** Fired when a page layout is applied to a document. */
public final class LayoutApplied implements DocumentEvent {

  private final DocumentId documentId;
  private final PageLayout pageLayout;
  private final Instant occurredAt;

  private LayoutApplied(DocumentId documentId, PageLayout pageLayout) {
    this.documentId = Validate.notNull(documentId, "documentId must not be null");
    this.pageLayout = Validate.notNull(pageLayout, "pageLayout must not be null");
    this.occurredAt = Instant.now();
  }

  public static LayoutApplied of(DocumentId documentId, PageLayout pageLayout) {
    return new LayoutApplied(documentId, pageLayout);
  }

  public DocumentId documentId() {
    return documentId;
  }

  public PageLayout pageLayout() {
    return pageLayout;
  }

  @Override
  public Instant occurredAt() {
    return occurredAt;
  }
}
