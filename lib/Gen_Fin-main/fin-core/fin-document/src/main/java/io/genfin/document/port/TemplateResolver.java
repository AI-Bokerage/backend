package io.genfin.document.port;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.CompiledTemplate;

public interface TemplateResolver {
  CompiledTemplate resolve(TemplateId id);
}
