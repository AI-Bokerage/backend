package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.PlaceholderValue;
import java.util.Map;
import java.util.Optional;

public final class TemplateVariables {

  private static final TemplateVariables EMPTY = new TemplateVariables(Map.of());

  private final Map<String, PlaceholderValue> values;

  private TemplateVariables(Map<String, PlaceholderValue> values) {
    this.values = Map.copyOf(values);
  }

  public static TemplateVariables empty() {
    return EMPTY;
  }

  public static TemplateVariables of(Map<String, PlaceholderValue> values) {
    return new TemplateVariables(values);
  }

  public Optional<PlaceholderValue> get(String key) {
    return Optional.ofNullable(values.get(key));
  }

  Map<String, PlaceholderValue> asMap() {
    return values;
  }
}
