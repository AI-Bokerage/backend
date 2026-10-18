package io.genfin.document.api.exception;

import io.genfin.api.exception.GenFinException;
import io.genfin.document.api.identity.RendererId;
import java.util.Map;

public final class RendererNotFoundException extends GenFinException {

  public RendererNotFoundException(RendererId rendererId) {
    super(
        DocumentErrorCode.RENDERER_NOT_FOUND,
        "No renderer registered for id: " + rendererId,
        null,
        Map.of("rendererId", rendererId));
  }
}
