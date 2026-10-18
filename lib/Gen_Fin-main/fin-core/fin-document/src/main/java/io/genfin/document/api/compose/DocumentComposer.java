package io.genfin.document.api.compose;

import io.genfin.document.api.model.DocumentModel;

public interface DocumentComposer {
  ComposedDocument compose(DocumentModel model);
}
