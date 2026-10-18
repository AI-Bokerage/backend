package io.genfin.api.config;

import io.genfin.api.exception.ConfigurationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable, typed configuration bag. Framework-agnostic — no Spring, no property-file parsing.
 */
public final class CoreConfiguration {

  private final Map<String, Object> values;

  private CoreConfiguration(Map<String, Object> values) {
    this.values = Map.copyOf(values);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static CoreConfiguration empty() {
    return new CoreConfiguration(Map.of());
  }

  public <T> T get(String key, Class<T> type) {
    return find(key, type)
        .orElseThrow(
            () -> new ConfigurationException("Missing required configuration key: " + key));
  }

  public <T> T get(String key, Class<T> type, T defaultValue) {
    return find(key, type).orElse(defaultValue);
  }

  public <T> Optional<T> find(String key, Class<T> type) {
    Object value = values.get(key);
    if (value == null) {
      return Optional.empty();
    }
    if (!type.isInstance(value)) {
      throw new ConfigurationException(
          "Configuration key '" + key + "' is of type " + value.getClass() + ", expected " + type);
    }
    return Optional.of(type.cast(value));
  }

  public boolean contains(String key) {
    return values.containsKey(key);
  }

  public static final class Builder {

    private final Map<String, Object> values = new HashMap<>();

    public Builder set(String key, Object value) {
      values.put(key, value);
      return this;
    }

    public CoreConfiguration build() {
      return new CoreConfiguration(values);
    }
  }
}
