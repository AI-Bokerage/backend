package io.genfin.document.api.template;

import io.genfin.document.api.identity.TemplateId;

public final class IncludeFragment implements TemplateFragment {

  private final TemplateId includedTemplateId;

  private IncludeFragment(TemplateId includedTemplateId) {
    this.includedTemplateId = includedTemplateId;
  }

  public static IncludeFragment of(TemplateId includedTemplateId) {
    return new IncludeFragment(includedTemplateId);
  }

  public TemplateId includedTemplateId() {
    return includedTemplateId;
  }

  @Override
  public <R> R accept(TemplateFragmentVisitor<R> visitor) {
    return visitor.visitInclude(this);
  }
}
