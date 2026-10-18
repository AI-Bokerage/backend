package io.genfin.document.api.template;

public interface TemplateFragmentVisitor<R> {
  R visitLiteral(LiteralFragment fragment);

  R visitPlaceholder(PlaceholderFragment fragment);

  R visitIf(IfFragment fragment);

  R visitEach(EachFragment fragment);

  R visitInclude(IncludeFragment fragment);
}
