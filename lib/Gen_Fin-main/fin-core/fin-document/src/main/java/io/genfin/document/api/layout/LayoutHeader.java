package io.genfin.document.api.layout;

public final class LayoutHeader {

  private final String text;

  private LayoutHeader(String text) {
    this.text = text == null ? "" : text;
  }

  public static LayoutHeader of(String text) {
    return new LayoutHeader(text);
  }

  public String text() {
    return text;
  }
}
