package io.genfin.document.api.preview;

import io.genfin.document.api.identity.RendererId;

/**
 * Options for {@link io.genfin.document.port.PreviewRenderer}, e.g. which renderer to preview with.
 */
public final class PreviewConfiguration {

  private final RendererId targetRenderer;

  private PreviewConfiguration(Builder builder) {
    this.targetRenderer =
        builder.targetRenderer == null ? RendererId.of("pdf") : builder.targetRenderer;
  }

  public static Builder builder() {
    return new Builder();
  }

  public RendererId targetRenderer() {
    return targetRenderer;
  }

  public static final class Builder {

    private RendererId targetRenderer;

    private Builder() {}

    public Builder targetRenderer(RendererId targetRenderer) {
      this.targetRenderer = targetRenderer;
      return this;
    }

    public PreviewConfiguration build() {
      return new PreviewConfiguration(this);
    }
  }
}
