package io.genfin.api.event;

/**
 * A fact that happened to a domain aggregate. Implementations belong to their owning finance
 * module.
 */
public interface DomainEvent {

  EventMetadata metadata();
}
