package io.genfin.document.api.layout;

import io.genfin.api.validation.Validate;

public final class PageLayout {

  private final PaperSize paperSize;
  private final Orientation orientation;
  private final Margins margins;
  private final LayoutHeader header;
  private final LayoutFooter footer;
  private final PageNumberConfiguration pageNumbers;

  private PageLayout(Builder builder) {
    Validate.notNull(builder.paperSize, "paperSize must not be null");
    Validate.notNull(builder.orientation, "orientation must not be null");
    this.paperSize = builder.paperSize;
    this.orientation = builder.orientation;
    this.margins = builder.margins == null ? Margins.none() : builder.margins;
    this.header = builder.header == null ? LayoutHeader.of("") : builder.header;
    this.footer = builder.footer == null ? LayoutFooter.of("") : builder.footer;
    this.pageNumbers =
        builder.pageNumbers == null ? PageNumberConfiguration.disabled() : builder.pageNumbers;
  }

  public static Builder builder(PaperSize paperSize, Orientation orientation) {
    return new Builder(paperSize, orientation);
  }

  public PaperSize paperSize() {
    return paperSize;
  }

  public Orientation orientation() {
    return orientation;
  }

  public Margins margins() {
    return margins;
  }

  public LayoutHeader header() {
    return header;
  }

  public LayoutFooter footer() {
    return footer;
  }

  public PageNumberConfiguration pageNumbers() {
    return pageNumbers;
  }

  public static final class Builder {

    private final PaperSize paperSize;
    private final Orientation orientation;
    private Margins margins;
    private LayoutHeader header;
    private LayoutFooter footer;
    private PageNumberConfiguration pageNumbers;

    private Builder(PaperSize paperSize, Orientation orientation) {
      this.paperSize = paperSize;
      this.orientation = orientation;
    }

    public Builder margins(Margins margins) {
      this.margins = margins;
      return this;
    }

    public Builder header(LayoutHeader header) {
      this.header = header;
      return this;
    }

    public Builder footer(LayoutFooter footer) {
      this.footer = footer;
      return this;
    }

    public Builder pageNumbers(PageNumberConfiguration pageNumbers) {
      this.pageNumbers = pageNumbers;
      return this;
    }

    public PageLayout build() {
      return new PageLayout(this);
    }
  }
}
