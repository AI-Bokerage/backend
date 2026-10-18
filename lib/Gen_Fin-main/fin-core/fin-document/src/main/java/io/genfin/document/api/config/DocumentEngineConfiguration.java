package io.genfin.document.api.config;

import io.genfin.document.api.identity.BrandId;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.layout.Margins;
import io.genfin.document.api.layout.Orientation;
import io.genfin.document.api.layout.PageNumberConfiguration;
import io.genfin.document.api.layout.PaperSize;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import java.util.Optional;

public final class DocumentEngineConfiguration {

  private final RendererId defaultRenderer;
  private final PaperSize defaultPaperSize;
  private final Orientation defaultOrientation;
  private final Optional<BrandId> defaultBrand;
  private final Optional<LetterheadId> defaultLetterhead;
  private final Margins defaultMargins;
  private final PageNumberConfiguration defaultPageNumbers;
  private final boolean previewEnabled;
  private final boolean watermarkEnabled;

  private DocumentEngineConfiguration(Builder builder) {
    this.defaultRenderer =
        builder.defaultRenderer == null ? RendererId.of("json") : builder.defaultRenderer;
    this.defaultPaperSize =
        builder.defaultPaperSize == null ? StandardPaperSize.A4 : builder.defaultPaperSize;
    this.defaultOrientation =
        builder.defaultOrientation == null
            ? StandardOrientation.PORTRAIT
            : builder.defaultOrientation;
    this.defaultBrand = Optional.ofNullable(builder.defaultBrand);
    this.defaultLetterhead = Optional.ofNullable(builder.defaultLetterhead);
    this.defaultMargins = builder.defaultMargins == null ? Margins.none() : builder.defaultMargins;
    this.defaultPageNumbers =
        builder.defaultPageNumbers == null
            ? PageNumberConfiguration.disabled()
            : builder.defaultPageNumbers;
    this.previewEnabled = builder.previewEnabled;
    this.watermarkEnabled = builder.watermarkEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public RendererId defaultRenderer() {
    return defaultRenderer;
  }

  public PaperSize defaultPaperSize() {
    return defaultPaperSize;
  }

  public Orientation defaultOrientation() {
    return defaultOrientation;
  }

  public Optional<BrandId> defaultBrand() {
    return defaultBrand;
  }

  public Optional<LetterheadId> defaultLetterhead() {
    return defaultLetterhead;
  }

  public Margins defaultMargins() {
    return defaultMargins;
  }

  public PageNumberConfiguration defaultPageNumbers() {
    return defaultPageNumbers;
  }

  public boolean previewEnabled() {
    return previewEnabled;
  }

  public boolean watermarkEnabled() {
    return watermarkEnabled;
  }

  public static final class Builder {

    private RendererId defaultRenderer;
    private PaperSize defaultPaperSize;
    private Orientation defaultOrientation;
    private BrandId defaultBrand;
    private LetterheadId defaultLetterhead;
    private Margins defaultMargins;
    private PageNumberConfiguration defaultPageNumbers;
    private boolean previewEnabled;
    private boolean watermarkEnabled;

    private Builder() {}

    public Builder defaultRenderer(RendererId defaultRenderer) {
      this.defaultRenderer = defaultRenderer;
      return this;
    }

    public Builder defaultPaperSize(PaperSize defaultPaperSize) {
      this.defaultPaperSize = defaultPaperSize;
      return this;
    }

    public Builder defaultOrientation(Orientation defaultOrientation) {
      this.defaultOrientation = defaultOrientation;
      return this;
    }

    public Builder defaultBrand(BrandId defaultBrand) {
      this.defaultBrand = defaultBrand;
      return this;
    }

    public Builder defaultLetterhead(LetterheadId defaultLetterhead) {
      this.defaultLetterhead = defaultLetterhead;
      return this;
    }

    public Builder defaultMargins(Margins defaultMargins) {
      this.defaultMargins = defaultMargins;
      return this;
    }

    public Builder defaultPageNumbers(PageNumberConfiguration defaultPageNumbers) {
      this.defaultPageNumbers = defaultPageNumbers;
      return this;
    }

    public Builder previewEnabled(boolean previewEnabled) {
      this.previewEnabled = previewEnabled;
      return this;
    }

    public Builder watermarkEnabled(boolean watermarkEnabled) {
      this.watermarkEnabled = watermarkEnabled;
      return this;
    }

    public DocumentEngineConfiguration build() {
      return new DocumentEngineConfiguration(this);
    }
  }
}
