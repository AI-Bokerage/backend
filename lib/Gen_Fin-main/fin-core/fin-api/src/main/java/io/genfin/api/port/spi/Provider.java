package io.genfin.api.port.spi;

@FunctionalInterface
public interface Provider<T> {

  T provide();
}
