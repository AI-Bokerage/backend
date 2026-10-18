package io.genfin.document.api.placeholder;

import io.genfin.document.port.PlaceholderFormatter;
import io.genfin.document.port.PlaceholderResolver;
import java.util.Map;

public final class PlaceholderContext {

  private final PlaceholderResolver resolver;
  private final Map<String, PlaceholderValue> locals;
  private final Map<String, PlaceholderFormatter> formatters;

  private PlaceholderContext(
      PlaceholderResolver resolver,
      Map<String, PlaceholderValue> locals,
      Map<String, PlaceholderFormatter> formatters) {
    this.resolver = resolver;
    this.locals = Map.copyOf(locals);
    this.formatters = Map.copyOf(formatters);
  }

  public static PlaceholderContext of(PlaceholderResolver resolver) {
    return new PlaceholderContext(resolver, Map.of(), Map.of());
  }

  public PlaceholderContext withLocal(String name, PlaceholderValue value) {
    Map<String, PlaceholderValue> merged = new java.util.HashMap<>(locals);
    merged.put(name, value);
    return new PlaceholderContext(resolver, merged, formatters);
  }

  public PlaceholderContext withFormatter(String key, PlaceholderFormatter formatter) {
    Map<String, PlaceholderFormatter> merged = new java.util.HashMap<>(formatters);
    merged.put(key, formatter);
    return new PlaceholderContext(resolver, locals, merged);
  }

  /**
   * Returns a copy of this context backed by {@code newResolver} instead of the current resolver,
   * keeping the same formatters but discarding locals. Used to layer a different fallback
   * resolution strategy (e.g. per-{{#each}}-item resolution) onto a context while preserving
   * formatters registered on the original, without letting the original's locals take precedence
   * over {@code newResolver}'s own item-vs-outer fallback logic.
   */
  public PlaceholderContext withResolver(PlaceholderResolver newResolver) {
    return new PlaceholderContext(newResolver, Map.of(), formatters);
  }

  public PlaceholderValue resolveValue(String key) {
    PlaceholderValue local = locals.get(key);
    return local != null ? local : resolver.resolve(key);
  }

  public String resolveDisplay(String key) {
    PlaceholderValue value = resolveValue(key);
    String raw =
        value.accept(
            new PlaceholderValueVisitor<String>() {
              @Override
              public String visitScalar(ScalarPlaceholderValue v) {
                return v.value();
              }

              @Override
              public String visitList(ListPlaceholderValue v) {
                return "";
              }

              @Override
              public String visitMissing(MissingPlaceholderValue v) {
                return "";
              }
            });
    PlaceholderFormatter formatter = formatters.get(key);
    return formatter != null ? formatter.format(raw) : raw;
  }
}
