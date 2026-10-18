package io.genfin.document.internal.template;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.compose.DocumentComposer;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentModel;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.api.template.TemplateContext;
import io.genfin.document.port.PlaceholderResolver;
import io.genfin.document.port.TemplateEngine;
import java.util.Optional;

public final class TemplateDocumentComposer implements DocumentComposer {

  private final TemplateEngine engine;

  public TemplateDocumentComposer(TemplateEngine engine) {
    this.engine = engine;
  }

  @Override
  public ComposedDocument compose(DocumentModel model) {
    Optional<String> templateId = model.attributes().get("templateId");
    if (templateId.isEmpty()) {
      return ComposedDocument.of(model.metadata(), model.sections(), model.attributes());
    }

    PlaceholderResolver resolver =
        key -> ScalarPlaceholderValue.of(model.attributes().get(key).orElse(""));
    var renderedSections =
        engine.render(TemplateId.of(templateId.get()), TemplateContext.of(resolver));

    return ComposedDocument.of(model.metadata(), renderedSections, model.attributes());
  }
}
