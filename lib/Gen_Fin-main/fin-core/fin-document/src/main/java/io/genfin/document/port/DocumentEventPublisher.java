package io.genfin.document.port;

import io.genfin.document.api.event.DocumentEvent;

/** Publishes document lifecycle events to registered {@link DocumentEventListener}s. */
public interface DocumentEventPublisher {

  void publish(DocumentEvent event);
}
