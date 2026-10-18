package io.genfin.document.api.brand;

public final class BrandFooter {

  private final String text;

  private BrandFooter(String text) {
    this.text = text == null ? "" : text;
  }

  public static BrandFooter of(String text) {
    return new BrandFooter(text);
  }

  public String text() {
    return text;
  }
}
