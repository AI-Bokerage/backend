package io.genfin.document.internal.event;

import io.genfin.document.api.event.DocumentEvent;
import io.genfin.document.port.DocumentEventListener;
import io.genfin.document.port.DocumentEventPublisher;
import java.util.ArrayList;
import java.util.List;

/** Default {@link DocumentEventPublisher} that fans out to registered listeners in order. */
public final class DefaultDocumentEventPublisher implements DocumentEventPublisher {

  private final List<DocumentEventListener> listeners = new ArrayList<>();

  public void register(DocumentEventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void publish(DocumentEvent event) {
    for (DocumentEventListener listener : listeners) {
      listener.onEvent(event);
    }
  }
}
