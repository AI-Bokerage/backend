package io.genfin.document.api.template;

import io.genfin.document.api.placeholder.Placeholder;
import java.util.List;

public final class EachFragment implements TemplateFragment {

  private final Placeholder collection;
  private final List<TemplateFragment> body;

  private EachFragment(Placeholder collection, List<TemplateFragment> body) {
    this.collection = collection;
    this.body = List.copyOf(body);
  }

  public static EachFragment of(Placeholder collection, List<TemplateFragment> body) {
    return new EachFragment(collection, body);
  }

  public Placeholder collection() {
    return collection;
  }

  public List<TemplateFragment> body() {
    return body;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitEach(this);
  }
}
