package io.genfin.invoice.metadata;

import io.genfin.api.domain.ValueObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Namespaced {@link Metadata} bags, so unrelated plugins/integrations never collide on a field
 * name.
 */
public record ExtensionProperties(Map<String, Metadata> byNamespace) implements ValueObject {

  public ExtensionProperties {
    byNamespace = Map.copyOf(byNamespace);
  }

  public static ExtensionProperties empty() {
    return new ExtensionProperties(Map.of());
  }

  public Optional<Metadata> find(String namespace) {
    return Optional.ofNullable(byNamespace.get(namespace));
  }

  public ExtensionProperties with(String namespace, Metadata metadata) {
    Map<String, Metadata> updated = new LinkedHashMap<>(byNamespace);
    updated.put(namespace, metadata);
    return new ExtensionProperties(updated);
  }
}
