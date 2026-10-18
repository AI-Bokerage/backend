package io.genfin.document.api.model;

public final class TextBlockElement implements DocumentElement {

  private final String text;

  private TextBlockElement(String text) {
    this.text = text == null ? "" : text;
  }

  public static TextBlockElement of(String text) {
    return new TextBlockElement(text);
  }

  public String text() {
    return text;
  }

  @Override
  public <R> R accept(DocumentElementVisitor<R> visitor) {
    return visitor.visitTextBlock(this);
  }
}
