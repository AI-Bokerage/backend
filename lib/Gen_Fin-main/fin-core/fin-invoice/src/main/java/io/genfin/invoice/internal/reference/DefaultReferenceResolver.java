package io.genfin.invoice.internal.reference;

import io.genfin.invoice.port.reference.ReferenceResolver;
import io.genfin.invoice.reference.Reference;
import java.util.Optional;

public final class DefaultReferenceResolver implements ReferenceResolver {

  @Override
  public Optional<String> resolveDisplayName(Reference reference) {
    return reference
        .label()
        .or(() -> Optional.of(reference.type().code() + ":" + reference.value().value()));
  }
}
