package io.genfin.invoice.attachment;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * A {@link DocumentReference} attached to an invoice under an application-defined relation label.
 */
public record AttachmentReference(DocumentReference document, String relation)
    implements ValueObject {

  public AttachmentReference {
    Validate.notNull(document, "document must not be null.");
    Validate.notBlank(relation, "relation must not be blank.");
  }
}
