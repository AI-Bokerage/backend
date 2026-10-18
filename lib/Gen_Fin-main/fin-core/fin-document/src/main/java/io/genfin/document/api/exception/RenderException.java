package io.genfin.document.api.exception;

import io.genfin.api.exception.GenFinException;
import io.genfin.document.api.identity.RendererId;
import java.util.Map;

public final class RenderException extends GenFinException {

  private final RendererId rendererId;

  public RenderException(RendererId rendererId, String message) {
    this(rendererId, message, null);
  }

  public RenderException(RendererId rendererId, String message, Throwable cause) {
    super(DocumentErrorCode.RENDER_FAILED, message, cause, Map.of("rendererId", rendererId));
    this.rendererId = rendererId;
  }

  public RendererId rendererId() {
    return rendererId;
  }
}
