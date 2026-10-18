package io.genfin.document.api.compose;

import io.genfin.document.internal.NoOpDocumentComposer;

public final class DocumentComposers {

  private DocumentComposers() {}

  public static DocumentComposer noOp() {
    return new NoOpDocumentComposer();
  }

  public static DocumentComposer templated(io.genfin.document.port.TemplateEngine engine) {
    return new io.genfin.document.internal.template.TemplateDocumentComposer(engine);
  }
}
