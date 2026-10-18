package io.genfin.api.internal.spi;

import io.genfin.api.spi.ExtensionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultExtensionRegistry implements ExtensionRegistry {

  private final Map<Class<?>, List<Object>> extensions = new ConcurrentHashMap<>();

  @Override
  public <T> void register(Class<T> extensionPoint, T implementation) {
    extensions.computeIfAbsent(extensionPoint, key -> new ArrayList<>()).add(implementation);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Optional<T> find(Class<T> extensionPoint) {
    return findAll(extensionPoint).stream().findFirst();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> List<T> findAll(Class<T> extensionPoint) {
    List<Object> found = extensions.get(extensionPoint);
    if (found == null) {
      return List.of();
    }
    return found.stream().map(o -> (T) o).toList();
  }
}
