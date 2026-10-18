package io.genfin.api.domain;

/**
 * Marker for immutable objects defined entirely by their attributes (equality by value, not
 * identity). Implementations should be {@code record}s so equals/hashCode fall out for free.
 */
public interface ValueObject extends DomainObject {}
