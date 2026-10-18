package io.genfin.invoice.port.reference;

import io.genfin.api.port.spi.Extension;
import io.genfin.invoice.reference.Reference;
import java.util.Optional;

/**
 * Resolves a display name for a {@link Reference} without the engine ever knowing what the
 * reference means.
 */
public interface ReferenceResolver extends Extension {

  Optional<String> resolveDisplayName(Reference reference);
}
