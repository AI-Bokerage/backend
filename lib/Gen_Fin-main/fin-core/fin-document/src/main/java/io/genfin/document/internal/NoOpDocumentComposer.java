package io.genfin.document.internal;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.compose.DocumentComposer;
import io.genfin.document.api.model.DocumentModel;

public final class NoOpDocumentComposer implements DocumentComposer {

  @Override
  public ComposedDocument compose(DocumentModel model) {
    return ComposedDocument.of(model.metadata(), model.sections(), model.attributes());
  }
}
