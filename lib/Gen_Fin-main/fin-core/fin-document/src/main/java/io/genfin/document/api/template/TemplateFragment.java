package io.genfin.document.api.template;

public interface TemplateFragment {
  <R> R accept(TemplateFragmentVisitor<R> visitor);
}
