package io.genfin.invoice.attachment;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.invoice.id.DocumentId;
import java.net.URI;

/**
 * A pointer to a document living wherever the application stores it — this engine never touches
 * file bytes.
 */
public record DocumentReference(DocumentId id, URI location, String mediaType)
    implements ValueObject {

  public DocumentReference {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(location, "location must not be null.");
  }

  public static DocumentReference of(URI location, String mediaType) {
    return new DocumentReference(DocumentId.generate(), location, mediaType);
  }
}
