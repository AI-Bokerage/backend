package io.genfin.document.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.document.api.event.DocumentEvent;

/** Extension point for consumers who want to observe document lifecycle events. */
public interface DocumentEventListener extends Extension {

  void onEvent(DocumentEvent event);
}
