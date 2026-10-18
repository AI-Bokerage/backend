package io.genfin.document.internal.template;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.CompiledTemplate;
import io.genfin.document.port.TemplateResolver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultTemplateResolver implements TemplateResolver {

  private final DefaultTemplateRegistry registry;
  private final Map<TemplateId, CompiledTemplate> cache = new ConcurrentHashMap<>();

  public DefaultTemplateResolver(DefaultTemplateRegistry registry) {
    this.registry = registry;
  }

  @Override
  public CompiledTemplate resolve(TemplateId id) {
    return cache.computeIfAbsent(
        id,
        templateId -> {
          io.genfin.document.api.template.DocumentTemplate template = registry.get(templateId);
          return TemplateCompiler.compile(template, registry::lookup);
        });
  }
}
