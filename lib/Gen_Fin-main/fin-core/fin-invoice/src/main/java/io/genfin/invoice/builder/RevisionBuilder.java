package io.genfin.invoice.builder;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.reference.Reference;
import io.genfin.invoice.reference.ReferenceType;

/**
 * Creates a new draft invoice that is a revision of an existing one, linked back via a {@link
 * Reference}.
 */
public final class RevisionBuilder {

  private RevisionBuilder() {}

  public static Invoice reviseOf(
      Invoice source,
      ReferenceType revisionReferenceType,
      ClockProvider clockProvider,
      String actor) {
    Invoice revision = CloneBuilder.clone(source, clockProvider, actor);
    revision.addReference(
        Reference.of(
            revisionReferenceType, source.id().value(), "Revision of " + source.id().value()),
        clockProvider,
        actor);
    return revision;
  }
}
