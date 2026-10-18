package io.genfin.api.port.spi;

import java.util.Optional;

@FunctionalInterface
public interface Resolver<K, T> {

  Optional<T> resolve(K key);
}
