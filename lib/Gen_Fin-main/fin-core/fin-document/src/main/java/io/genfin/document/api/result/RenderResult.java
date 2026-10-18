package io.genfin.document.api.result;

import io.genfin.document.api.identity.RendererId;
import java.util.Arrays;

public final class RenderResult {

  private final RendererId rendererId;
  private final byte[] content;
  private final String mimeType;

  private RenderResult(RendererId rendererId, byte[] content, String mimeType) {
    this.rendererId = rendererId;
    this.content = Arrays.copyOf(content, content.length);
    this.mimeType = mimeType;
  }

  public static RenderResult of(RendererId rendererId, byte[] content, String mimeType) {
    return new RenderResult(rendererId, content, mimeType);
  }

  public RendererId rendererId() {
    return rendererId;
  }

  public byte[] content() {
    return Arrays.copyOf(content, content.length);
  }

  public String mimeType() {
    return mimeType;
  }
}
