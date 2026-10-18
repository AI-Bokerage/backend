package io.genfin.api.port.spi;

@FunctionalInterface
public interface Factory<T> {

  T create();
}
