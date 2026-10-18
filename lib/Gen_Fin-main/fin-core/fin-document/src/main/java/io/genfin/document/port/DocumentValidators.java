package io.genfin.document.port;

import io.genfin.document.internal.validation.DefaultDocumentValidator;

/**
 * Factory for a standard, ready-to-use {@link DocumentValidator}. This is the only
 * consumer-reachable way to obtain a working {@link DocumentValidator}: the concrete implementation
 * lives in an internal package that the module never exports.
 */
public final class DocumentValidators {

  private DocumentValidators() {}

  public static DocumentValidator of(RendererResolver resolver) {
    return new DefaultDocumentValidator(resolver);
  }
}
