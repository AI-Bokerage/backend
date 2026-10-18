package io.genfin.api.port.spi;

@FunctionalInterface
public interface Strategy<IN, OUT> {

  OUT apply(IN input);
}
