package io.genfin.api.port.id;

/**
 * Extension point for identifier value generation. Consumers must never instantiate UUIDs directly.
 */
public interface IdentifierGenerator {

  String generate();
}
