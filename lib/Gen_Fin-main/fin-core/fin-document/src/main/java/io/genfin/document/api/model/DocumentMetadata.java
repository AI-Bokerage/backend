package io.genfin.document.api.model;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.DocumentId;
import java.time.Instant;

/** Identity and descriptive metadata (type, locale, currency, creation time) for a document. */
public final class DocumentMetadata {

  private final DocumentId id;
  private final DocumentType type;
  private final String locale;
  private final String currency;
  private final Instant createdAt;

  private DocumentMetadata(
      DocumentId id, DocumentType type, String locale, String currency, Instant createdAt) {
    this.id = Validate.notNull(id, "DocumentMetadata id must not be null");
    this.type = Validate.notNull(type, "DocumentMetadata type must not be null");
    this.locale = locale;
    this.currency = currency;
    this.createdAt = createdAt;
  }

  public static DocumentMetadata of(
      DocumentId id, DocumentType type, String locale, String currency, Instant createdAt) {
    return new DocumentMetadata(id, type, locale, currency, createdAt);
  }

  public DocumentId id() {
    return id;
  }

  public DocumentType type() {
    return type;
  }

  public String locale() {
    return locale;
  }

  public String currency() {
    return currency;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
