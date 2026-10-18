package io.genfin.document.api.template;

public final class LiteralFragment implements TemplateFragment {

  private final String text;

  private LiteralFragment(String text) {
    this.text = text == null ? "" : text;
  }

  public static LiteralFragment of(String text) {
    return new LiteralFragment(text);
  }

  public String text() {
    return text;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitLiteral(this);
  }
}
