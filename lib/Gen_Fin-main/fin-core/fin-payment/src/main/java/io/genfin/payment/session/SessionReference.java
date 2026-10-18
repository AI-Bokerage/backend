package io.genfin.payment.session;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.payment.reference.Reference;

/** The application concept (invoice, order, ...) this session was opened for. */
public record SessionReference(Reference reference) implements ValueObject {

  public SessionReference {
    Validate.notNull(reference, "reference must not be null.");
  }
}
