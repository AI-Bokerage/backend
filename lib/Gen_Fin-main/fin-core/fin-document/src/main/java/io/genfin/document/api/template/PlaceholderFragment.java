package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.Placeholder;

public final class PlaceholderFragment implements TemplateFragment {

  private final Placeholder placeholder;

  private PlaceholderFragment(Placeholder placeholder) {
    this.placeholder = placeholder;
  }

  public static PlaceholderFragment of(Placeholder placeholder) {
    return new PlaceholderFragment(placeholder);
  }

  public Placeholder placeholder() {
    return placeholder;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitPlaceholder(this);
  }
}
