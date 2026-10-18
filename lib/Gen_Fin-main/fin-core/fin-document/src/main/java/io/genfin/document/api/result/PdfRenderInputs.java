package io.genfin.document.api.result;

import io.genfin.api.validation.Validate;
import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.qr.QrCodeContent;
import io.genfin.document.api.watermark.Watermark;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class PdfRenderInputs {

  private final PageLayout layout;
  private final Optional<BrandProfile> brandProfile;
  private final Optional<Letterhead> letterhead;
  private final Optional<Watermark> watermark;
  private final Map<String, QrCodeContent> qrCodes;

  private PdfRenderInputs(Builder builder) {
    Validate.notNull(builder.layout, "layout must not be null");
    this.layout = builder.layout;
    this.brandProfile = Optional.ofNullable(builder.brandProfile);
    this.letterhead = Optional.ofNullable(builder.letterhead);
    this.watermark = Optional.ofNullable(builder.watermark);
    this.qrCodes = Map.copyOf(builder.qrCodes);
  }

  public static Builder builder(PageLayout layout) {
    return new Builder(layout);
  }

  public PageLayout layout() {
    return layout;
  }

  public Optional<BrandProfile> brandProfile() {
    return brandProfile;
  }

  public Optional<Letterhead> letterhead() {
    return letterhead;
  }

  public Optional<Watermark> watermark() {
    return watermark;
  }

  public Map<String, QrCodeContent> qrCodes() {
    return qrCodes;
  }

  public static final class Builder {

    private final PageLayout layout;
    private BrandProfile brandProfile;
    private Letterhead letterhead;
    private Watermark watermark;
    private final Map<String, QrCodeContent> qrCodes = new HashMap<>();

    private Builder(PageLayout layout) {
      this.layout = layout;
    }

    public Builder brandProfile(BrandProfile brandProfile) {
      this.brandProfile = brandProfile;
      return this;
    }

    public Builder letterhead(Letterhead letterhead) {
      this.letterhead = letterhead;
      return this;
    }

    public Builder watermark(Watermark watermark) {
      this.watermark = watermark;
      return this;
    }

    public Builder qrCode(String key, QrCodeContent content) {
      this.qrCodes.put(key, content);
      return this;
    }

    public PdfRenderInputs build() {
      return new PdfRenderInputs(this);
    }
  }
}
