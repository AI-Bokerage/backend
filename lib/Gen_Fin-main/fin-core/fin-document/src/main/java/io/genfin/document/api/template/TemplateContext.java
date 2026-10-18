package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.PlaceholderContext;
import io.genfin.document.port.PlaceholderFormatter;
import io.genfin.document.port.PlaceholderResolver;
import java.util.HashMap;
import java.util.Map;

public final class TemplateContext {

  private final PlaceholderResolver resolver;
  private final TemplateVariables initialVariables;
  private final Map<String, PlaceholderFormatter> formatters;

  private TemplateContext(
      PlaceholderResolver resolver,
      TemplateVariables initialVariables,
      Map<String, PlaceholderFormatter> formatters) {
    this.resolver = resolver;
    this.initialVariables = initialVariables;
    this.formatters = Map.copyOf(formatters);
  }

  public static TemplateContext of(PlaceholderResolver resolver) {
    return new TemplateContext(resolver, TemplateVariables.empty(), Map.of());
  }

  public static TemplateContext of(
      PlaceholderResolver resolver, TemplateVariables initialVariables) {
    return new TemplateContext(resolver, initialVariables, Map.of());
  }

  public static TemplateContext of(
      PlaceholderResolver resolver,
      TemplateVariables initialVariables,
      Map<String, PlaceholderFormatter> formatters) {
    return new TemplateContext(resolver, initialVariables, formatters);
  }

  /** Returns a copy with {@code formatter} additionally registered for {@code key}. */
  public TemplateContext withFormatter(String key, PlaceholderFormatter formatter) {
    Map<String, PlaceholderFormatter> merged = new HashMap<>(formatters);
    merged.put(key, formatter);
    return new TemplateContext(resolver, initialVariables, merged);
  }

  public PlaceholderContext toPlaceholderContext() {
    PlaceholderContext context = PlaceholderContext.of(resolver);
    for (Map.Entry<String, io.genfin.document.api.placeholder.PlaceholderValue> entry :
        initialVariables.asMap().entrySet()) {
      context = context.withLocal(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, PlaceholderFormatter> entry : formatters.entrySet()) {
      context = context.withFormatter(entry.getKey(), entry.getValue());
    }
    return context;
  }
}
