package io.genfin.document.api.layout;

public final class LayoutFooter {

  private final String text;

  private LayoutFooter(String text) {
    this.text = text == null ? "" : text;
  }

  public static LayoutFooter of(String text) {
    return new LayoutFooter(text);
  }

  public String text() {
    return text;
  }
}
