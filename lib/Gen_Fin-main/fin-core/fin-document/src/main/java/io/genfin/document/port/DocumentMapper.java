package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.model.DocumentModel;

/** Converts a third-party source type {@code T} into the module's {@link DocumentModel}. */
public interface DocumentMapper<T> extends Extension {
  DocumentModel map(T source);
}
