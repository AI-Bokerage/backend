package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.Placeholder;
import java.util.List;

public final class IfFragment implements TemplateFragment {

  private final Placeholder condition;
  private final List<TemplateFragment> body;

  private IfFragment(Placeholder condition, List<TemplateFragment> body) {
    this.condition = condition;
    this.body = List.copyOf(body);
  }

  public static IfFragment of(Placeholder condition, List<TemplateFragment> body) {
    return new IfFragment(condition, body);
  }

  public Placeholder condition() {
    return condition;
  }

  public List<TemplateFragment> body() {
    return body;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitIf(this);
  }
}
