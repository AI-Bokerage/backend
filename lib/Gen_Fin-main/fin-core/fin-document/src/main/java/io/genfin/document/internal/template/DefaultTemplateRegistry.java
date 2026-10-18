package io.genfin.document.internal.template;

import io.genfin.document.api.exception.TemplateNotFoundException;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.DocumentTemplate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultTemplateRegistry {

  private final Map<TemplateId, DocumentTemplate> templates = new ConcurrentHashMap<>();

  public void register(DocumentTemplate template) {
    templates.put(template.id(), template);
  }

  public DocumentTemplate get(TemplateId id) {
    DocumentTemplate template = templates.get(id);
    if (template == null) {
      throw new TemplateNotFoundException(id);
    }
    return template;
  }

  DocumentTemplate lookup(TemplateId id) {
    return templates.get(id);
  }
}
