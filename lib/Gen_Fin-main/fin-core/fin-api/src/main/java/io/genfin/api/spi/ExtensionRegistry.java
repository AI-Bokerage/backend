package io.genfin.api.spi;

import java.util.List;
import java.util.Optional;

/** Registry of pluggable extension implementations, keyed by extension-point type. */
public interface ExtensionRegistry {

  <T> void register(Class<T> extensionPoint, T implementation);

  <T> Optional<T> find(Class<T> extensionPoint);

  <T> List<T> findAll(Class<T> extensionPoint);
}
