package io.genfin.document.api.brand;

public final class BrandHeader {

  private final String text;

  private BrandHeader(String text) {
    this.text = text == null ? "" : text;
  }

  public static BrandHeader of(String text) {
    return new BrandHeader(text);
  }

  public String text() {
    return text;
  }
}
