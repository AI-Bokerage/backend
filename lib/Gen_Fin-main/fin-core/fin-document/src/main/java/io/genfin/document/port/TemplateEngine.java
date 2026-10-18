package io.genfin.document.port;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.template.TemplateContext;
import java.util.List;

public interface TemplateEngine {
  List<DocumentSection> render(TemplateId templateId, TemplateContext context);
}
